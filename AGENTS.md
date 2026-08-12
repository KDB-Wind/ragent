# AGENTS.md（fork 基线版）

本文件是 KDB-Wind/ragent fork 的基线版，供 AI 代理（Codex / Claude Code / opencode 等）在本仓库工作时参考。内容基于上游 nageoffer/ragent 的结构精简而来；若与上游 README 或本 fork 实际代码冲突，以本 fork 代码为准。

## 语言偏好

与用户交流使用中文；代码、命令、变量名等技术标识符保持英文。

## 项目简介

Ragent 是一个企业级 Agentic RAG 智能体平台：后端 Java 17 + Spring Boot 3.5.7，前端 React 18 + TypeScript + Vite。覆盖文档入库 ETL、多路检索、意图识别、问题重写、会话记忆、模型路由容错、MCP 工具调用、全链路追踪与管理后台。

## 开发命令

后端（Maven 多模块，根目录）：

```bash
./mvnw -B -ntp spotless:check   # 格式检查（CI 门禁）
./mvnw -B -ntp spotless:apply   # 手动格式化
./mvnw -B -ntp -DskipTests package  # 编译构建（CI 用）
./mvnw -B -ntp test  # 单元测试（默认排除 @Tag("integration") 的集成测试）
./mvnw -B -ntp test -P integration  # opt-in 集成测试（需 PostgreSQL/Redis/Milvus/模型 API）
./mvnw -B -ntp -pl bootstrap -am -P integration -Dtest=QueryRewriteTests -Dsurefire.failIfNoSpecifiedTests=false test  # 集成测试类定向运行
```

注意：spotless `apply` 绑定在 compile 阶段，改动代码后本地构建会自动格式化；CI 以 `spotless:check` 作为门禁。

前端：

```bash
cd frontend
npm ci
npm run test    # Vitest 单元测试（jsdom，全部测试文件）
npm run test:coverage  # CI 使用：测试 + V8 coverage 低基线门禁
npm run lint
npm run build
npm run dev     # 开发服务器 5173，/api 代理到 localhost:9090
```

### 最小相关检查路由

局部改动先跑最小相关检查获得快速反馈，提交前仍按影响范围升级到完整门禁。完整映射与升级条件见 `docs/verification-routing.md`。

```bash
# 后端单个测试类；-am 场景必须关闭“未找到指定测试即失败”
./mvnw -B -ntp -pl bootstrap -am -Dtest=StreamChatTraceRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test

# 前端单个测试文件
cd frontend && npm run test -- src/hooks/__tests__/useStreamResponse.test.ts
```

## 模块分层

```text
bootstrap   -> infra-ai -> framework
mcp-server  -> (独立应用，无内部模块依赖)
```

- `bootstrap`：业务实现，依赖 infra-ai 与 framework
- `infra-ai`：屏蔽不同模型供应商差异，业务层不直接依赖供应商 SDK
- `framework`：通用能力，不放业务逻辑
- `mcp-server`：独立部署的 MCP Server（端口 9099）

## RAG 核心设计要点

- **多路检索**：`MultiChannelRetrievalEngine` 并行多通道检索 + `SearchResultPostProcessor` 后处理链（去重、重排序）
- **会话记忆**：区分"原始消息窗口 / 摘要记忆 / 最终送模上下文"三层，超限自动摘要压缩
- **模型路由与容错**：Chat / Embedding / Rerank 均为候选模型配置驱动，含优先级、失败阈值、熔断恢复；供应商差异留在 infra-ai
- **入库管线**：文档入库为 `IngestionNode` 节点编排 Pipeline（解析→增强→分块→向量化→写库）

## 配置与凭据

application.yaml 携带本地开发默认凭据，生产部署必须覆盖。`ProductionCredentialGuard`（`bootstrap` 模块，`EnvironmentPostProcessor`，Boot 3 机制注册）在启动最前置阶段执行 fail-fast：

- **放行条件**：未显式激活任何 profile，或全部 active profile 均属于 `local` / `dev` / `test`。只要集合中存在 `prod` / `staging` 等非开发 profile 就执行检查。
- **检查时机**：非开发 profile 下，对以下敏感键检查生效值；缺失、为空或命中开发默认值均抛 `IllegalStateException` 中断启动（消息只含键名，不含值）：
  - `spring.datasource.username`
  - `spring.datasource.password`
  - `spring.data.redis.password`
  - `rag.storage.s3.access-key` / `rag.storage.s3.secret-key`（仅 `rag.storage.type=s3`）
  - `rag.storage.oss.access-key` / `rag.storage.oss.secret-key`（仅 `rag.storage.type=oss`）
- **占位符检查**：无法解析的 `${KEY}`、解析为空的 `${KEY:}`、空白值以及命中开发默认值的 `${KEY:默认值}` 均会中断。
- **模型/API key**：仅被启用的模型、解析器或 Web Search provider 必须提供对应环境变量；对象存储 OSS key 属守卫范围，不归入模型 API key。

生产部署的完整键清单与示例见 `docs/production-configuration.md`。密钥轮换属部署侧运维，守卫不参与；轮换时保证新值先注入、旧值下线，避免空窗期。

## 扩展点

按 Spring Bean 自动发现，新增能力优先走扩展点而非改核心分发逻辑：

- 新增检索通道：实现 `SearchChannel`
- 新增后处理器：实现 `SearchResultPostProcessor`
- 新增 MCP 工具：实现 `MCPToolExecutor`
- 新增入库节点：实现 `IngestionNode`

## 上游同步

本 fork 与上游 `nageoffer/ragent` 的同步 SOP 见 `docs/upstream-sync.md`。以下为 Bash 示例；PowerShell 版本见完整 SOP：

```bash
git fetch upstream
sync_date=$(date +%Y%m%d)
git checkout -b "sync/upstream-$sync_date" origin/main
git merge upstream/main
./mvnw -B -ntp test        # 合并后必跑
cd frontend && npm ci && npm run lint && npm run test && npm run build
cd ..
git push origin "sync/upstream-$sync_date"
gh pr create --base main --head "sync/upstream-$sync_date" --title "chore: sync upstream main ($sync_date)"
```

冲突原则：测试基线（surefire 配置、vitest/RTL、CI workflow、AGENTS.md）保留 fork 版；上游业务代码不静默修改。

## CI 要求

- `backend-maven`：`spotless:check` 与 `./mvnw -B -ntp verify` 必须通过（verify 包含默认单元测试集合；依赖外部服务的集成测试已用 `@Tag("integration")` 隔离，通过 `-P integration` 显式运行）
- `frontend-build-lint`：`npm run lint`、`npm run test:coverage` 与 `npm run build` 均为硬门禁；当前 lint 基线为 0 error / 0 warning
- 2026-08-12 已通过 GitHub API 验证 `main-protection` active：无 bypass actor，只允许 PR 合入，strict required checks 为 `backend-maven` / `frontend-build-lint` / `dependency-review` / `Analyze (java-kotlin)` / `Analyze (javascript-typescript)`。
- 本项目是单人维护 fork，ruleset 不要求 approval 或 CODEOWNERS approval；AI review 是建议层，不是 approval，也无法合并 PR。若未来增加第二位维护者，再将独立人工 approval 设为必需。

## Code Review Rules

- DeepSeek 日常第一层审查只能由维护者在 PR 评论 `/deepseek-review` 手动触发；它只提供 COMMENTED 类型反馈，不构成 approval，不得修改或合并 PR。
- 官方 Codex 第二层审查仅在高风险或准备合并的 PR 上用 `@codex review` 手动触发；不开启 automatic reviews。
- 审查优先关注行为回归、认证授权、凭据泄露、SSRF、注入、资源清理、并发、配置门禁和测试缺口；格式与 lint 交给 CI。

## 协作 owner

模块 owner 见 `.github/CODEOWNERS`，review 时按 owner 路由。
