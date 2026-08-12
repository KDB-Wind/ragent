# Fork 治理改造审核与 P1 修复记录（2026-08-11）

## 1. 结论

初审结论为 **BLOCKED**：仓库内 CI、前端测试与供应链 workflow 的方向正确，本地门禁通过，但 remediation tracker 对部分 finding 的 `Closed` 声明超过了证据边界。阻断点不是 P0 漏洞，而是生产凭据守卫、集成测试隔离及 GitHub 强制策略仍存在未闭合或未验证边界。

本轮已开始修复仓库内 P1；GitHub ruleset 仅做只读核验，没有执行外部配置写操作。

## 2. 七个 findings 的初审判定

| Finding | 初审判定 | 核心理由 |
|---|---|---|
| `ci-branch-gate-missing` | 部分闭合 | CI job 已存在；初审包内没有 ruleset/bypass/required check 证据。 |
| `frontend-validation-gap` | 闭合（有注记） | 16 个行为测试覆盖 ChatInput、SSE parser、chat store，并纳入硬门禁。 |
| `production-default-credential-boundary` | 部分闭合 | 旧实现可被实际 active profile、混合 profile、空值及 OSS 配置绕过。 |
| `integration-isolation-gap` | 部分闭合 | `@Tag` 只解决何时运行，没有唯一资源、失败清理与清理后置断言。 |
| `collaboration-owner-route-gap` | 闭合（有注记） | 版本化治理入口已建立，但 bootstrap 细分 CODEOWNERS 路径错误。 |
| `dependency-supply-chain-gap` | 部分闭合 | Dependabot/CodeQL/Dependency Review 已存在；SCA、SBOM 与合并阻断仍未完成。 |
| `sse-diagnostic-verification-gap` | 部分闭合 | 前端 parser 测试已增加，脚本级 stderr、correlation id 与业务断言仍缺失。 |

## 3. 初审问题清单

### P0

未发现立即造成任意代码执行、数据破坏或凭据直接泄露的 P0 问题。

### P1

1. `ProductionCredentialGuard` 读取 `spring.profiles.active` 属性而非已解析的 active profile 集合，programmatic/include/group profile 可绕过。
2. `prod,dev` 因任一开发 profile 命中而整体放行；null、空串、空白与 `${KEY:}` 也被放行。
3. `rag.storage.type=oss` 时实际 OSS key 未检查，未启用的 S3 key 反而被无条件检查。
4. Milvus 集成测试会写共享 collection，但没有唯一资源、失败清理或清理 postcondition。
5. main ruleset 的 active 状态、required contexts 与 bypass 边界在原审核包中未验证。
6. CodeQL workflow 成功只证明扫描完成；没有 `required_code_scanning` 时不能称为漏洞阻断门禁。
7. `QueryRewriteTests` 文档命令缺 `-am` 和 `-P integration`，不能在干净 reactor 中正确运行目标测试。

### P2 摘要

- CODEOWNERS 的 bootstrap 细分路径不匹配真实源码路径，且注释错误描述为“最长路径优先”。
- Dependency Review 多授予 `pull-requests: write`，Actions 使用可变 major tag。
- batch06 的竞态清理与 admin callbacks 缺少行为回归测试；tracker 的 useCallback 数量声明不准确。
- lint 基线、SQL 脚本数量、Vite 代理端口、EnvironmentPostProcessor 注册路径等文档发生漂移。
- Maven compiler/surefire 插件未显式固定版本，构建输出稳定性警告。

## 4. GitHub 端只读复核

采集时间：2026-08-11（Asia/Shanghai），目标：`KDB-Wind/ragent`。

- 仓库为 public，默认分支为 `main`；vulnerability alerts 接口返回 `204`，已启用。
- ruleset `main-protection`（id `20637126`）为 `active`，准确覆盖 `refs/heads/main`。
- 已禁止分支删除和 non-fast-forward；PR 规则要求 1 个 approve、CODEOWNERS review、push 后撤销旧审批。
- required status checks 开启 strict policy，当前只要求 `backend-maven` 和 `frontend-build-lint`。
- HEAD `abcde4383d2f34cbdaf19a0bcd8610330e22091b` 的上述两个 required check 均成功；CodeQL 双语言分析也成功。
- ruleset 存在 `@KDB-Wind` 用户级 `pull_request` bypass。PR #8 没有 review 仍被合并，证明审批规则可由该 bypass 绕过。原 finding 要求“main 不能绕过 required checks”，因此在移除或重新设计 bypass 前仍只能部分闭合。
- Dependency Review 与 CodeQL 未列入 required status checks，ruleset 也没有 `required_code_scanning`；它们当前是扫描信号，不是完整合并门禁。
- 实时 open alerts：Dependabot 58（critical 1 / high 23 / medium 32 / low 2）；Code scanning 104（security severity critical 5 / high 2 / medium 65 / 未分级 32）。这些数量会变化，引用时必须附采集时间。

建议的 GitHub 端后续动作：先增加第二位有效 reviewer/CODEOWNER，再移除个人 bypass；将 `dependency-review` 设为 required，并为 CodeQL 配置 code-scanning merge protection。上述动作会改变外部仓库策略，需仓库管理员单独授权。

## 5. 本轮 P1 修复

### 5.1 生产凭据边界

- 改用 `environment.getActiveProfiles()`；仅无 profile 或全部为开发 profile 时放行。
- 混合 profile fail-closed。
- 基础凭据的 null、空串、空白、空默认占位符均失败。
- 按 `rag.storage.type` 条件检查 S3 或 OSS，拒绝未知存储类型。
- 单测由 9 个扩展到 15 个，并增加一次真实 `SpringApplication` 启动失败测试，验证 `spring.factories` 注册路径。

### 5.2 集成测试隔离

- `InvoiceIndexDocumentTests` 只按本次生成的唯一主键删除数据，并轮询验证清理结果。
- `MilvusCollectionTests` 每次使用 UUID collection，`finally` 删除，并断言 collection 不存在。
- 真实集成环境执行仍待 PostgreSQL/Redis/Milvus/模型服务就绪后完成，未执行前不得把运行效果写成“已验证”。

### 5.3 文档与命令

- 修正定向集成测试命令，补齐 `-pl bootstrap -am -P integration`。
- lint 更新为 0 error / 0 warning 硬门禁，前端校验统一包含 test。
- 上游同步 SOP 分离 Bash 与 PowerShell，SQL 数量修正为 9。
- 前端测试文档统一后端端口 9090，移除机器绝对路径和虚构运行状态。
- 文档明确区分“workflow 已存在”和“GitHub ruleset 已将其设为 required”。

## 6. 验证记录

初审阶段已通过：

- `./mvnw -B -ntp spotless:check`
- `./mvnw -B -ntp test`
- 前端 `npm run test`：16/16
- 前端 `npm run lint`：0 error / 0 warning
- 前端 `npm run build`

P1 修复阶段：

- 新凭据测试在旧实现上先出现 8 个预期失败；实现修复后 15/15 通过。
- 集成测试源码 `test-compile` 通过；真实 `-P integration` 未运行，因为需要外部服务。
- 最终验证已通过：`./mvnw -B -ntp spotless:check`、`./mvnw -B -ntp test`、前端 `npm run test`（16/16）、`npm run lint`（0 error / 0 warning）和 `npm run build`。
- `git diff --check` 通过。

## 7. 当前接受边界

- 生产凭据 finding：仓库代码层已修复并通过完整默认测试；部署侧 staging 启动验证仍属于发布验收。
- 集成隔离 finding：实现已补齐，真实外部环境验证前保持 Partial。
- CI/main finding：ruleset 和 check 名已验证，但个人 bypass 仍存在，保持 Partial。
- 供应链 finding：保持 Partial；不得把 CodeQL/Dependency Review 描述为已阻断合并。
