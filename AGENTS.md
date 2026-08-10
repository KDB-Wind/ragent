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
./mvnw -B -ntp test -P integration  # opt-in 集成测试（需 MySQL/Redis/Milvus/模型 API）
./mvnw -B -ntp test -pl bootstrap -Dtest=QueryRewriteTests  # 单模块/单测试
```

注意：spotless `apply` 绑定在 compile 阶段，改动代码后本地构建会自动格式化；CI 以 `spotless:check` 作为门禁。

前端：

```bash
cd frontend
npm ci
npm run test    # Vitest 单元测试（jsdom，全部测试文件）
npm run lint
npm run build
npm run dev     # 开发服务器 5173，/api 代理到 localhost:9090
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

- **放行条件**：未显式激活任何 profile，或激活 profile 集合含 `local` / `dev` / `test` 任一。本地直接启动（无 profile）行为保持不变。
- **检查时机**：其他 profile（如 `prod`）下，对以下敏感键检查生效值，命中开发默认值即抛 `IllegalStateException` 中断启动（消息只含键名，不含值）：
  - `spring.datasource.username`
  - `spring.datasource.password`
  - `spring.data.redis.password`
  - `rag.storage.s3.access-key`
  - `rag.storage.s3.secret-key`
- **占位符检查**：值以 `${` 开头且无默认值时（如 `${DB_PASSWORD}`），若环境变量/secret 无法解析同样中断启动；`${KEY:默认值}` 的默认值命中开发默认凭据集合也会中断。
- **模型 API key 走环境变量属正向机制**（`BAILIAN_API_KEY`、`SILICONFLOW_API_KEY`、`AIHUBMIX_API_KEY`、`MINERU_API_KEY`、`OSS_ACCESS_KEY`、`OSS_SECRET_KEY`、`YDC_API_KEY` 等），不参与守卫检查，但生产必须提供。

生产部署的完整键清单与示例见 `docs/production-configuration.md`。密钥轮换属部署侧运维，守卫不参与；轮换时保证新值先注入、旧值下线，避免空窗期。

## 扩展点

按 Spring Bean 自动发现，新增能力优先走扩展点而非改核心分发逻辑：

- 新增检索通道：实现 `SearchChannel`
- 新增后处理器：实现 `SearchResultPostProcessor`
- 新增 MCP 工具：实现 `MCPToolExecutor`
- 新增入库节点：实现 `IngestionNode`

## 上游同步

本 fork 与上游 `nageoffer/ragent` 的同步 SOP 见 `docs/upstream-sync.md`。常用命令：

```bash
git fetch upstream
git checkout -b sync/upstream-$(date +%Y%m%d) origin/main
git merge upstream/main
./mvnw -B -ntp test        # 合并后必跑
cd frontend && npm ci && npm run test && npm run build
git push origin sync/upstream-<日期>
gh pr create --base main --head sync/upstream-<日期> --title "chore: sync upstream main (<日期>)"
```

冲突原则：测试基线（surefire 配置、vitest/RTL、CI workflow、AGENTS.md）保留 fork 版；上游业务代码不静默修改。

## CI 要求

- `backend-maven`：`spotless:check` 与 `./mvnw -B -ntp verify` 必须通过（verify 包含默认单元测试集合；依赖外部服务的集成测试已用 `@Tag("integration")` 隔离，通过 `-P integration` 显式运行）
- `frontend-build-lint`：`npm run build` 与 `npm run test` 为硬门禁；`npm run lint` 已启用并记录结果，但存量 21 个 lint error 为既有技术债，暂不阻塞（修复列入后续计划）
- main 分支受 ruleset 保护：只能通过 PR 合入，需要 1 个 approve + CODEOWNERS review + 上述 check 全绿

## 协作 owner

模块 owner 见 `.github/CODEOWNERS`，review 时按 owner 路由。
