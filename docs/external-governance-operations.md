# 外部治理操作手册

本文记录无法仅靠仓库代码闭合、必须由仓库管理员或部署负责人执行的治理事项。操作对象为 `KDB-Wind/ragent`；实时状态和告警数量可能变化，执行时以 GitHub 页面/API 为准。

## 1. 推荐执行顺序

1. 在 PR #9 合并后复核默认分支 CodeQL/Dependabot 存量告警。
2. 在隔离的真实依赖环境运行集成测试。
3. 在 staging 完成生产凭据守卫的正向、负向和混合 profile 验收。
4. 执行 API key 轮换和数据库升级等部署侧操作。

2026-08-12 已完成的 GitHub 端操作：`main-protection` 已移除所有 bypass actor，保留必须通过 PR 合入，并将后端、前端、Dependency Review 及 CodeQL 双语言 job 设为 strict required checks。

## 2. 增加独立 Reviewer

当前决策：个人项目暂不增加第二位 Reviewer，该项为未来协作扩展选项，不是当前闭合前置条件。

1. 进入 `Settings → Collaborators and teams → Add people`。
2. 添加至少一名可信 Reviewer，授予 `Write` 或 `Maintain` 权限。
3. 将 `.github/CODEOWNERS` 的默认规则更新为：

   ```text
   * @KDB-Wind @trusted-reviewer
   ```

4. 通过正常 PR 合并 CODEOWNERS 修改。
5. 创建测试 PR，由新增 Reviewer 完成 CODEOWNERS approval。

完成证据：协作者页面截图或 API 输出、CODEOWNERS PR、有效 approval 记录。

未来增加可信维护者后，再按上述步骤开启 `1 approval + CODEOWNERS review`。AI review 只是建议层，不冒充独立人工 approval。

## 3. 收紧 main ruleset

责任人：Repo Admin。

当前 ruleset：`main-protection`，id `20637126`；已验证 active 且 bypass list 为空。

1. 进入 `Settings → Rules → Rulesets → main-protection → Edit`。
2. 确认 `Bypass list` 保持为空。
3. 保持以下设置：
   - target 为 `refs/heads/main`；
   - ruleset 为 Active；
   - 禁止 deletion 和 non-fast-forward；
   - 只允许通过 PR 合并；
   - 单人维护期间 approval count 为 0，不要求 CODEOWNERS review；
   - strict required status checks。
4. 保存后创建测试 PR，确认未审批时不能合并、审批后仍必须等待全部 required checks。

若必须保留紧急通道，优先迁入 Organization，并使用受控团队作为窄范围 bypass actor；每次使用都应关联 issue/PR 和事后复盘，不使用个人常驻 bypass。

完成证据：ruleset JSON/截图、测试 PR 链接、无审批时 Merge 按钮被阻断的记录。

## 4. 提升供应链合并门禁

责任人：Repo Admin。

### 4.1 Dependency Review

1. 先确认某个近期 PR 已成功产生 `dependency-review` check。
2. 在 `main-protection` 的 required status checks 中增加精确 context：`dependency-review`。
3. 保留现有 `backend-maven` 和 `frontend-build-lint`。
4. 用修改依赖的测试 PR 验证检查出现并参与合并判定。

当前 workflow 的 `fail-on-severity: high` 会阻止 PR 引入 High/Critical 的依赖变化，但不会自动清理存量 Dependabot 告警。

### 4.2 CodeQL

当前矩阵 check 的精确名称为：

- `Analyze (java-kotlin)`
- `Analyze (javascript-typescript)`

可以将两个 check 加入 required status checks，保证两个语言分析都成功完成；不要只依赖聚合名称 `CodeQL`。

分析 job 成功不等于“没有漏洞”。在处理或判定当前 Critical/High 告警后，还应在 ruleset 中配置 `Require code scanning results`/Code Scanning merge protection，并从 High 或更高严重度开始设置阻断阈值。若当前套餐或 UI 不提供该规则，只能把扫描 job 作为 required check，并保留人工安全复核，此时供应链 finding 仍是 Partial。

完成证据：ruleset required contexts、Code Scanning rule/阈值、同时触发双语言扫描的测试 PR。

## 5. 安全告警处置

责任人：代码 Owner 负责修复，Repo Admin 负责最终 dismiss/接受风险。

2026-08-12 修复前只读 API 快照：

- Dependabot：58 open（1 Critical / 23 High / 32 Medium / 2 Low）。
- Code scanning：104 open（5 Critical / 2 High / 65 Medium / 32 未分级）。

按 Critical → High → Medium → Low/未分级处理；优先生产运行时直接依赖和外部可达的数据流。每个告警通过独立 PR 修复并关联告警编号。只有确认是误报、不可达路径或明确接受风险时才 dismiss，必须填写技术理由，不以“清零数字”为目的批量 dismiss。

仓库内已修复当前 5 个 Critical SSRF、1 个 High 鉴权绕过和 1 个 High 前端不完整转义，并增加定向回归测试；前端本地 npm audit 已无 Critical/High。PR #9 的 CodeQL 重扫已通过且无本 PR 新增告警；默认分支存量 CodeQL/Dependabot 告警仍需在合并后按最新 API 快照逐条 triage，不要为清零数字批量 dismiss。

2026-08-13 再次分页读取默认分支得到 99 个 open（2 Critical / 65 Medium / 32 未分级）。SSE 诊断后续分支同时消除其所触及文件中的 3 个存量 `java/log-injection` 数据流；是否正式关闭以该 PR CodeQL 扫描和合并后的默认分支快照为准。

完成证据：修复 PR、重新扫描结果、关闭或带理由 dismiss 的告警记录。

## 6. 真实集成测试验收

责任人：具备测试基础设施和 secret 权限的部署/测试负责人。

准备与生产隔离的 PostgreSQL、Redis、Milvus、对象存储和模型 API；不得指向生产数据。仓库已提供只可手动触发的 `.github/workflows/integration.yml`，具体 Environment、runner 标签和 secret 清单见 `docs/protected-integration-tests.md`。配置审批后优先从 Actions 运行；本地等价命令为：

```powershell
.\mvnw.cmd -B -ntp -P integration test
```

至少连续执行两次并确认：

- 两次均成功；
- collection/document/chunk 不重名；
- 本轮资源被精确清理；
- 不删除其他测试或既存数据；
- Surefire 报告和服务端日志已归档；
- 日志不包含凭据值。

在完成真实运行前，`integration-isolation-gap` 保持 Partial。

## 7. staging 凭据守卫验收

责任人：部署负责人。

执行三组测试：

1. 负向：激活 `prod`/`staging`，缺少一个必需凭据；应用应立即非零退出，日志只出现配置键名，不出现值。
2. 正向：从 staging secret store 注入全部适用凭据；守卫应通过并进入正常启动流程。按实际部署分别验证 S3 或 OSS 分支。
3. 混合 profile：激活 `prod,dev`；仍应执行生产检查，不能因包含 `dev` 绕过。

完成证据：脱敏启动日志、部署 run id、profile 与存储类型记录。真实密钥不得写入 issue、PR、日志或本仓库。

## 8. 其他部署侧收尾

- 轮换曾明文出现在本机 opencode 配置中的 mygpt API key；验证新 key 生效后吊销旧 key。
- 按 `docs/v1.1.0-upgrade-guide.md` 备份并执行数据库升级，保存升级和回滚验证记录。
- 生产密钥轮换遵循“先注入新值、验证生效、再下线旧值”，避免空窗期。

## 9. Findings 闭合条件

| Finding | 外部闭合条件 |
|---|---|
| `ci-branch-gate-missing` | 已闭合：active ruleset 无 bypass，必须 PR，五个 strict required checks 已由 PR #9 验证 |
| `integration-isolation-gap` | 隔离环境连续两次真实集成测试通过，且清理 postcondition 有证据 |
| `dependency-supply-chain-gap` | Dependency Review required；Code Scanning merge protection 生效；Critical/High 存量完成修复或有依据的判定 |
| `production-default-credential-boundary` | 代码层已闭合；staging 正/负/混合 profile 验收作为发布接受证据 |

## 10. Better Harness 复评后的个人待办（2026-08-13）

以下事项不能由无真实凭据、基础设施或仓库管理员最终判断的代理代做：

1. 在隔离环境连续运行两次 protected integration workflow，保存 doctor、测试、cleanup/reset postcondition 和脱敏日志。
2. 在 staging 执行生产凭据守卫的负向、正向、混合 profile 验收。
3. 轮换曾暴露于本机配置的 API key，并在验证新 key 后吊销旧 key。
4. 备份后执行 v1.1.0 数据库升级，保存升级和回滚验证证据。
5. PR 合并后复核默认分支的 CodeQL/Dependabot 最新分页快照；逐条修复或给出技术判定，不批量 dismiss。
6. 若 GitHub 套餐支持，在 `main-protection` 增加 Code Scanning 结果严重度规则；否则继续保留双语言 required jobs 和人工告警验收。
7. 在未来至少两个真实开发任务中保存“目标 → 改动 → 最小检查 → 完整检查 → PR 接受结果”，再做纵向 Better Harness 复评。

仓库内已经能先完成的部分是 SSE 关联诊断和 affected-check 路由；这不替代第 1、5、7 项的真实运行与长期证据。
