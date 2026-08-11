# Better Harness 治理跟踪（Remediation Tracker）

本文件记录 Better Harness 审计（2026-08-10）暴露问题的修复状态、已完成事项与剩余路线。审计基线与报告详见下文，本文件是 fork 治理的单一入口，相关 SOP 见文末索引。

## 1. 审计背景

- 审计工具：Better Harness 0.5.0（Codex 官方插件入口，三路 evidence pass + render validation）
- 审计基线 commit：`6368bca`（fork main，同步上游前）
- 权威报告（审计副本，不入库）：
  `D:\Code_Study\RAgent-harness-audit\.codex\better-harness\run-20260810\{findings.json, report.md, report.html}`
- 补充维护规划（审计副本）：`.opencode\better-harness\maintenance-roadmap.md`

## 2. Findings 状态

| Finding | 严重度 | 状态 | 闭合依据 |
|---|---|---|---|
| `ci-branch-gate-missing` 合并前无强制 CI 与 main 保护 | High | ✅ Closed | PR #1：GitHub Actions CI + main ruleset（required checks + PR 审批） |
| `frontend-validation-gap` 前端无行为级验证 | Medium | ✅ Closed | PR #2：Vitest/RTL 16 用例；PR #6：lint 46→0 并改为 CI 硬门禁 |
| `production-default-credential-boundary` 默认凭据未隔离 | Medium | ✅ Closed | PR #4：`ProductionCredentialGuard` fail-fast + 生产配置文档 |
| `integration-isolation-gap` 集成测试无隔离回收 | Medium | ✅ Closed | PR #2：`@Tag("integration")` + `-P integration` opt-in |
| `collaboration-owner-route-gap` 协作责任无 owner 路由 | Low | ✅ Closed | PR #1：CODEOWNERS/PR/Issue 模板/AGENTS.md |
| `dependency-supply-chain-gap` 供应链无门禁 | Low | ⏳ Partial | PR #7：Dependabot + CodeQL + Dependency Review + alerts 启用；**SCA/SBOM/镜像扫描未做**（见 4.2） |
| `sse-diagnostic-verification-gap` SSE 验证只查传输状态 | Low | ⏳ Partial | PR #2：前端 6 个 SSE 解析单测；**脚本级诊断增强未做**（并入可观测性，见 4.3） |

五维基线（2026-08-10）：任务理解 58 / 可控执行 49 / 改动验证 46 / 可靠交付 35 / 经验沉淀 35。首次复评在 61–90 天窗口（见 4.5）。

## 3. 已完成事项（7 个 PR，12 个功能提交，19 个 commit）

| PR | 标题 | 对应事项 | 合并 commit |
|---|---|---|---|
| #1 | CI + main 保护 + 协作契约 | 4.1/4.2/4.3：`.github/workflows/ci.yml`、ruleset `main-protection`（id 20637126）、CODEOWNERS、PR/Issue 模板、AGENTS.md、SECURITY.md、CONTRIBUTING.md | `e273d68` |
| #2 | 前端测试基线 + 后端集成测试隔离 | 4.4：Vitest/RTL/MSW 16 用例、`@Tag("integration")` 10 类、pom surefire excludedGroups + `-P integration` | `bb5edc4` |
| #3 | 同步上游 main（2026-08-10） | 4.6：合并上游 47 commits（图谱/智能体/v1.1.0 SQL/存储抽象/检索重构）、`docs/upstream-sync.md` SOP | `e98bc4f` |
| #4 | 生产配置 fail-fast 守卫 | 4.5：`ProductionCredentialGuard`（EnvironmentPostProcessor）+ 9 单测 + `docs/production-configuration.md` | `e67cfa8` |
| #5 | v1.1.0 SQL 升级指南 | `docs/v1.1.0-upgrade-guide.md`（备份/顺序/验证清单） | `f59dbf7` |
| #6 | 前端 lint 清零 + 硬门禁 | P1：46 problems→0（拆 4 文件、14 useCallback、4 finally 重构、2 @ts-nocheck 移除、死目录 `frontend/@/` 删除）；lint 移除 continue-on-error | `8e0fbd9` |
| #7 | 供应链门禁第一批 | P2：Dependabot（Maven+npm）、CodeQL（双语言）、Dependency Review；启用 vulnerability alerts | `9cab4cf` |

其他已交付：vulnerability alerts 启用（公开仓库免费），依赖图谱生效；Better Harness 安装于 Codex/Claude Code/OpenCode 三端（审计副本 verification/ 下有安装证据）。

## 4. 剩余事项

### 4.1 需人工/外部操作（不阻塞开发）

- [ ] **轮换 mygpt API key**：曾明文存于 opencode 配置（已迁移 auth.json；fork/上游全历史扫描零泄露），供应商侧轮换一次收尾
- [ ] **执行 v1.1.0 SQL 升级**：本地与部署库均需执行（先备份，按 `docs/v1.1.0-upgrade-guide.md`）
- [ ] **消化 Dependabot 漏洞告警**：当前 24 个 open（1 critical / 9 high / 13 medium / 1 low，集中在前端 npm 链路：axios×7、js-yaml×3、postcss×3、brace-expansion×2、react-router×2 等）；Dependabot 自动 PR 到达后按 critical→high 优先合入；后端 Maven 无 open alerts
- [ ] **定期上游同步**（建议月例行）：按 `docs/upstream-sync.md` SOP

### 4.2 P2 第二批（供应链收尾）

- [ ] SCA 独立扫描：OWASP Dependency-Check（Maven，注意 NVD 数据源限制，建议 weekly schedule 而非每次 PR）或 Trivy fs
- [ ] SBOM 生成：CycloneDX Maven 插件 + 前端 `@cyclonedx/cyclonedx-npm`，与 release 产物关联
- [ ] 镜像漏洞扫描（Docker 建立后随镜像管道；Trivy action）

### 4.3 可观测性阶段（31–60 天，含 P3）

- [ ] SSE 诊断脚本增强：`scripts/sse_queue_test.sh` 捕获 stderr、correlation id 关联、业务断言（区分传输/服务端/模型/依赖失败）
- [ ] coverage ratchet：后端 JaCoCo + 前端 vitest coverage，先基线后递增，不设过高阈值
- [ ] 可观测性：结构化日志、metrics（模型首包/检索通道/降级次数/SSE 断开原因）、OpenTelemetry tracing、health/liveness、Trace ID 跨前后端
- [ ] release 流程：SemVer/changelog/GitHub Release/制品 checksum
- [ ] Docker/部署 ADR：环境分层、DB migration、备份恢复、RTO/RPO
- [ ] Actions 升级：setup-java/setup-node v4→v5（当前有 Node 20 runtime deprecation 提示）

### 4.4 61–90 天

- [ ] staged CD：staging 自动部署 → smoke → 审批 → prod（同一已验证制品晋升）
- [ ] 容器镜像扫描与签名（Cosign）、SBOM 与制品关联、部署前验签
- [ ] rollback/canary 策略与定期恢复演练
- [ ] 生产配置默认值 fail-fast 的部署侧复核（配合 4.5 的守卫）

### 4.5 复评

- [ ] 首次 Better Harness 复评：对比基线五维分数与 findings 状态，验证改进效果（需积累至少两个可比 Task Episode 才有 session 证据；届时可运行 `/better-harness` 生成新报告归档至审计副本）

## 5. 相关文档索引

- 上游同步 SOP：`docs/upstream-sync.md`
- v1.1.0 数据库升级：`docs/v1.1.0-upgrade-guide.md`
- 生产配置与凭据：`docs/production-configuration.md`
- 协作与 CI 规范：根 `AGENTS.md`、`CONTRIBUTING.md`、`.github/CODEOWNERS`
