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
| `ci-branch-gate-missing` 合并前无强制 CI 与 main 保护 | High | ✅ Closed | active main ruleset 无 bypass，只允许 PR，五个 strict required checks 已用 PR #9 验证 |
| `frontend-validation-gap` 前端无行为级验证 | Medium | ✅ Closed | Vitest/RTL 18 用例 + coverage ratchet；lint 46→0 并改为 CI 硬门禁 |
| `production-default-credential-boundary` 默认凭据未隔离 | Medium | ✅ Closed | `ProductionCredentialGuard` 使用实际 active profiles、空值 fail-fast、S3/OSS 条件检查 + 15 单测（含真实 Spring 启动负例） |
| `integration-isolation-gap` 集成测试无隔离回收 | Medium | ⏳ Partial | opt-in + Milvus 唯一资源/精确清理/postcondition 已实现；真实外部环境运行待验证 |
| `collaboration-owner-route-gap` 协作责任无 owner 路由 | Low | ✅ Closed | PR #1：CODEOWNERS/PR/Issue 模板/AGENTS.md |
| `dependency-supply-chain-gap` 供应链无门禁 | Low | ⏳ Partial | Dependabot/CodeQL/Dependency Review + CycloneDX 双端 SBOM + weekly Trivy SCA 已实现；双语言扫描 job 已 required，但存量告警、Code Scanning 严重度规则和制品签名仍待处理 |
| `sse-diagnostic-verification-gap` SSE 验证只查传输状态 | Low | ⏳ Partial | 前端 SSE 解析测试 + 脚本 stderr/headers/correlation ID/业务完成断言已实现；尚未对真实服务完成端到端运行 |

五维基线（2026-08-10）：任务理解 58 / 可控执行 49 / 改动验证 46 / 可靠交付 35 / 经验沉淀 35。首次复评在 61–90 天窗口（见 4.5）。

## 3. 已完成事项（7 个 PR，12 个功能提交，19 个 commit）

| PR | 标题 | 对应事项 | 合并 commit |
|---|---|---|---|
| #1 | CI + main 保护 + 协作契约 | 4.1/4.2/4.3：`.github/workflows/ci.yml`、ruleset `main-protection`（id 20637126）、CODEOWNERS、PR/Issue 模板、AGENTS.md、SECURITY.md、CONTRIBUTING.md | `e273d68` |
| #2 | 前端测试基线 + 后端集成测试隔离 | 4.4：Vitest/RTL/MSW 16 用例、`@Tag("integration")` 11 类、pom surefire excludedGroups + `-P integration` | `bb5edc4` |
| #3 | 同步上游 main（2026-08-10） | 4.6：合并上游 47 commits（图谱/智能体/v1.1.0 SQL/存储抽象/检索重构）、`docs/upstream-sync.md` SOP | `e98bc4f` |
| #4 | 生产配置 fail-fast 守卫 | 4.5：`ProductionCredentialGuard`（EnvironmentPostProcessor）+ 9 单测 + `docs/production-configuration.md` | `e67cfa8` |
| #5 | v1.1.0 SQL 升级指南 | `docs/v1.1.0-upgrade-guide.md`（备份/顺序/验证清单） | `f59dbf7` |
| #6 | 前端 lint 清零 + 硬门禁 | P1：46 problems→0（拆 4 文件、16 useCallback、4 finally 重构、2 @ts-nocheck 移除、死目录 `frontend/@/` 删除）；lint 移除 continue-on-error | `8e0fbd9` |
| #7 | 供应链门禁第一批 | P2：Dependabot（Maven+npm）、CodeQL（双语言）、Dependency Review；启用 vulnerability alerts | `9cab4cf` |

其他已交付：vulnerability alerts 启用（2026-08-11 API 返回 204），依赖图谱生效；Better Harness 安装于 Codex/Claude Code/OpenCode 三端（审计副本 verification/ 下有安装证据）。

### 3.1 外部复核与 P1 跟进（2026-08-11）

- ruleset `main-protection`（id `20637126`）已确认 active、覆盖 `refs/heads/main`、无 bypass actor，且只允许 PR 合入。
- 单人维护模式下 approval/CODEOWNERS approval 设为 0/false；这是明确的可用性取舍，AI review 不伪装为独立人工 approval。
- strict required checks 已扩展为 `backend-maven`、`frontend-build-lint`、`dependency-review`、`Analyze (java-kotlin)` 和 `Analyze (javascript-typescript)`；PR #9 五项及 CodeQL 聚合结果全绿。
- 生产凭据守卫已补 active profile、混合 profile、空值、S3/OSS 条件检查和真实 Spring 启动负例，测试扩展为 15 个。
- Milvus 写入测试已使用唯一主键/collection，并在 `finally` 精确清理和验证 postcondition；待专用集成环境执行。
- PR #9 已完成 GitHub CodeQL 重扫，本 PR 的 3 个 SSRF 变体已清零，聚合检查通过；默认分支的存量计数需等合并后再刷新。
- 前端 `npm audit --audit-level=high` 已无 Critical/High；GitHub Dependabot 告警变化待重新扫描。
- PR #11（merge commit `cd3a1c0`）补强 LightRAG URL 规范化与 IPv6/IDN 回归测试；合并后 main 的 CI 和 CodeQL 双语言 job 均成功。分页 API 复核仍有 99 个 open CodeQL 告警（2 Critical / 65 Medium / 32 未分级）和 4 个 Medium Dependabot 告警；扫描 job 成功不代表告警已关闭。
- 完整审核和修复证据见 `docs/governance-remediation-review-20260811.md`。

## 4. 剩余事项

### 4.1 需人工/外部操作（不阻塞开发）

详细步骤、责任人、顺序和完成证据见 `docs/external-governance-operations.md`。第二位 Reviewer 对当前个人项目不再是前置条件；无 bypass 的 CI 门禁已落地。

- [ ] **轮换 mygpt API key**：曾明文存于 opencode 配置（已迁移 auth.json；fork/上游全历史扫描零泄露），供应商侧轮换一次收尾
- [ ] **执行 v1.1.0 SQL 升级**：本地与部署库均需执行（先备份，按 `docs/v1.1.0-upgrade-guide.md`）
- [ ] **消化 Dependabot 漏洞告警**：2026-08-13 分页 API 快照为 4 个 Medium；Draft PR #12 已升级 React Router 与 PrismJS 依赖，Dependency Review 全绿，本地 `npm audit` 为 0；默认分支告警需合并后确认关闭
- [ ] **消化 CodeQL 告警**：2026-08-13 分页 API 快照为 99 个 open（2 Critical SSRF / 65 Medium / 32 未分级）；Draft PR #12 已处理 4 个锁释放和 4 个空指针路径，CodeQL 双语言全绿且 PR 级新增告警为 0；存量告警是否关闭需合并后复核
- [x] **收紧 main bypass**：个人 bypass 已移除；单人维护模式下不强制 approval
- [ ] **定期上游同步**（建议月例行）：按 `docs/upstream-sync.md` SOP

### 4.2 P2 第二批（供应链收尾）

- [x] 将 `dependency-review` 和 CodeQL 双语言 job 提升为 strict required checks
- [ ] 若当前 GitHub 套餐/UI 支持，再配置 `required_code_scanning` 严重度阈值；否则保留双语言 required jobs + 人工告警 triage
- [x] SCA 独立扫描：weekly/manual/release-tag Trivy SBOM scan，双端独立 SARIF；存量 signal-only
- [x] SBOM 生成：CycloneDX Maven 插件 + 前端 `@cyclonedx/cyclonedx-npm`，随 workflow artifact 保存
- [ ] 镜像漏洞扫描（Docker 建立后随镜像管道；Trivy action）

### 4.3 可观测性阶段（31–60 天，含 P3）

- [x] SSE 诊断脚本增强：捕获 stderr/headers/raw SSE、correlation ID、curl 与业务完成断言；真实端到端运行待外部验证
- [x] coverage ratchet：后端 JaCoCo 报告 + 前端 Vitest V8 低基线硬阈值，见 `docs/coverage-baseline.md`
- [ ] 可观测性：结构化日志、metrics（模型首包/检索通道/降级次数/SSE 断开原因）、OpenTelemetry tracing、health/liveness、Trace ID 跨前后端
- [ ] release 流程：SemVer/changelog/GitHub Release/制品 checksum
- [ ] Docker/部署 ADR：环境分层、DB migration、备份恢复、RTO/RPO
- [x] Actions 升级：checkout v7、setup-java v5、CodeQL Action v4（全部固定完整 SHA）

### 4.4 61–90 天

- [ ] staged CD：staging 自动部署 → smoke → 审批 → prod（同一已验证制品晋升）
- [ ] 容器镜像扫描与签名（Cosign）、SBOM 与制品关联、部署前验签
- [ ] rollback/canary 策略与定期恢复演练
- [ ] 生产配置默认值 fail-fast 的部署侧复核（配合 4.5 的守卫）

### 4.5 复评

- [ ] 首次 Better Harness 复评：对比基线五维分数与 findings 状态，验证改进效果（需积累至少两个可比 Task Episode 才有 session 证据；届时可运行 `/better-harness` 生成新报告归档至审计副本）

## 5. 相关文档索引

- 外部治理人工操作手册：`docs/external-governance-operations.md`
- 上游同步 SOP：`docs/upstream-sync.md`
- v1.1.0 数据库升级：`docs/v1.1.0-upgrade-guide.md`
- 生产配置与凭据：`docs/production-configuration.md`
- 覆盖率基线：`docs/coverage-baseline.md`
- 受保护集成测试：`docs/protected-integration-tests.md`
- 协作与 CI 规范：根 `AGENTS.md`、`CONTRIBUTING.md`、`.github/CODEOWNERS`
