# 改动到验证的精确路由

本文给代理和维护者提供局部改动的最小反馈入口。局部检查用于尽早发现问题，不替代提交前或 PR 上的完整 CI。

## 路由表

| 改动范围 | 首个最小检查 | 提交前升级 |
|---|---|---|
| `framework/` 通用上下文、Web/SSE 工具 | `./mvnw -B -ntp -pl framework test` | 根目录 `./mvnw -B -ntp verify` |
| `infra-ai/` 模型回调、路由、容错 | `./mvnw -B -ntp -pl infra-ai -am test` | 根目录 `./mvnw -B -ntp verify` |
| SSE Trace、pipeline、取消 | `./mvnw -B -ntp -pl bootstrap -am -Dtest=StreamChatTraceRunnerTest,StreamChatPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test` | 根目录 `./mvnw -B -ntp verify`；真实依赖行为另走 protected integration workflow |
| 生产凭据守卫 | `./mvnw -B -ntp -pl bootstrap -am -Dtest=ProductionCredentialGuardTest -Dsurefire.failIfNoSpecifiedTests=false test` | 根目录 `./mvnw -B -ntp verify`；发布前需 staging 验收 |
| 前端单个 hook/component | `cd frontend && npm run test -- <test-file>` | `npm run lint && npm run test:coverage && npm run build` |
| Maven/npm 依赖或 workflow | 受影响模块构建、本地 audit/语法检查 | PR 上等待 Dependency Review、CodeQL 和全部 required checks |
| `@Tag("integration")` 测试或外部适配器 | 不在无真实依赖的本机伪运行 | 手动 protected integration workflow，保存清理 postcondition |

## 升级规则

- 跨模块接口、根 `pom.xml`、共享类型或公共回调发生变化：直接升级到根目录 `verify`。
- 前后端协议字段发生变化：后端定向测试和前端对应解析测试都要运行，再跑两端完整门禁。
- 触及数据库、Redis、Milvus、对象存储或模型 API 的真实交互：单元测试通过仍只能说明代码边界；必须在隔离环境跑 opt-in integration。
- 触及认证、授权、URL 获取、反序列化、日志或凭据：除功能测试外，必须等待 PR CodeQL/Dependency Review；扫描 job 成功不等于存量告警已处置。

PowerShell 调用 Maven 时，可将 `-Dtest=...` 和 `-Dsurefire.failIfNoSpecifiedTests=false` 分别放在双引号内，避免参数被 shell 误解析。
