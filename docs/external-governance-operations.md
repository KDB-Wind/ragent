# 外部治理操作手册

本文记录无法仅靠仓库代码闭合、必须由仓库管理员或部署负责人执行的治理事项。操作对象为 `KDB-Wind/ragent`；实时状态和告警数量可能变化，执行时以 GitHub 页面/API 为准。

## 1. 推荐执行顺序

1. 增加第二位可信 Reviewer/CODEOWNER。
2. 验证第二位 Reviewer 能完成 CODEOWNERS approval。
3. 移除 `@KDB-Wind` 的个人 `pull_request` bypass。
4. 将 Dependency Review 和 CodeQL 安全策略提升为合并门禁。
5. 处理 Critical/High 安全告警，再启用相应 Code Scanning 阻断阈值。
6. 在隔离的真实依赖环境运行集成测试。
7. 在 staging 完成生产凭据守卫的正向、负向和混合 profile 验收。
8. 执行 API key 轮换和数据库升级等部署侧操作。

不要在只有一名有效 Reviewer 时先移除个人 bypass。当前直接协作者和默认 CODEOWNER 只有 `KDB-Wind`；直接移除 bypass 后，`1 approve + CODEOWNERS review` 可能使仓库无人能够批准作者自己的 PR。

## 2. 增加独立 Reviewer

责任人：Repo Admin。

1. 进入 `Settings → Collaborators and teams → Add people`。
2. 添加至少一名可信 Reviewer，授予 `Write` 或 `Maintain` 权限。
3. 将 `.github/CODEOWNERS` 的默认规则更新为：

   ```text
   * @KDB-Wind @trusted-reviewer
   ```

4. 通过正常 PR 合并 CODEOWNERS 修改。
5. 创建测试 PR，由新增 Reviewer 完成 CODEOWNERS approval。

完成证据：协作者页面截图或 API 输出、CODEOWNERS PR、有效 approval 记录。

如果项目保持单人维护，则无法同时满足“独立审批”和“无个人 bypass”。应书面接受该风险，并保持 `ci-branch-gate-missing` 为 Partial，不得标记 Closed。

## 3. 收紧 main ruleset

责任人：Repo Admin。

当前 ruleset：`main-protection`，id `20637126`；当前个人 bypass 为 `@KDB-Wind / pull_request`。

1. 进入 `Settings → Rules → Rulesets → main-protection → Edit`。
2. 在 `Bypass list` 中移除个人 bypass。
3. 保持以下设置：
   - target 为 `refs/heads/main`；
   - ruleset 为 Active；
   - 禁止 deletion 和 non-fast-forward；
   - 只允许通过 PR 合并；
   - 至少 1 个 approval；
   - required CODEOWNERS review；
   - push 后撤销旧 approval；
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

仓库内已修复当前 5 个 Critical SSRF、1 个 High 鉴权绕过和 1 个 High 前端不完整转义，并增加定向回归测试；前端本地 npm audit 已无 Critical/High。管理员仍须触发 CodeQL/Dependabot 重新扫描，逐条确认告警确实关闭且没有新变体；扫描完成前保持“未验证”，不要手工批量 dismiss。

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
| `ci-branch-gate-missing` | 第二 Reviewer 生效；个人 bypass 移除/重新设计；测试 PR 证明审批与 checks 均不可绕过 |
| `integration-isolation-gap` | 隔离环境连续两次真实集成测试通过，且清理 postcondition 有证据 |
| `dependency-supply-chain-gap` | Dependency Review required；Code Scanning merge protection 生效；Critical/High 存量完成修复或有依据的判定 |
| `production-default-credential-boundary` | 代码层已闭合；staging 正/负/混合 profile 验收作为发布接受证据 |
