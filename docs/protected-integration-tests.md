# 受保护的集成测试

`.github/workflows/integration.yml` 只允许通过 `workflow_dispatch` 手动触发，并绑定 GitHub
Environment `integration`。它不会由 pull request、push 或定时任务自动执行。

## GitHub 管理员一次性配置

1. 在 **Settings → Environments** 创建 `integration`。
2. 为该 Environment 配置 required reviewers；建议同时启用“阻止发起者自审”。
3. 注册隔离的 Linux self-hosted runner，并同时添加 `linux` 与 `ragent-integration` 标签。
   仅该 runner 应能访问测试数据库、Redis、Milvus 与对象存储；不要在生产网络运行。
4. 在 `integration` Environment 中创建以下 secrets：

| Secret | 用途 |
| --- | --- |
| `INTEGRATION_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `INTEGRATION_DATASOURCE_USERNAME` / `INTEGRATION_DATASOURCE_PASSWORD` | 测试库凭据 |
| `INTEGRATION_REDIS_HOST` / `INTEGRATION_REDIS_PORT` / `INTEGRATION_REDIS_PASSWORD` | 测试 Redis |
| `INTEGRATION_S3_ENDPOINT` / `INTEGRATION_S3_ACCESS_KEY` / `INTEGRATION_S3_SECRET_KEY` | 测试对象存储 |
| `INTEGRATION_MILVUS_URI` | 测试 Milvus URI |
| `INTEGRATION_BAILIAN_API_KEY` | Bailian 模型测试 |
| `INTEGRATION_SILICONFLOW_API_KEY` | SiliconFlow embedding 测试 |

所有服务必须使用可丢弃的测试数据与最小权限账号。当前集成测试会调用真实模型 API，并可能创建数据库、缓存、向量集合与对象；不应指向生产资源。

## 每次运行

在 **Actions → Protected Integration Tests → Run workflow** 选择待验证分支。Environment
审批通过后，工作流先检查必需配置是否存在（只输出缺少的键名，不输出值），再运行：

```bash
./mvnw -B -ntp -P integration test
```

无论成功或失败，Surefire/Failsafe 报告都会作为 artifact 保留 14 天。Environment、runner、
secrets 与实际运行结果均属于 GitHub 端状态；仅凭仓库文件不能判定其已配置或已通过。
