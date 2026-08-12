# 生产部署配置说明

本文档说明 fork 在生产 profile 下的凭据覆盖要求与 fail-fast 守卫行为。所有示例均使用占位符，不包含真实凭据。

## 1. 配置来源与开发默认值

`bootstrap/src/main/resources/application.yaml` 为本地开发默认配置，包含一组开发默认凭据（本地数据库口令、对象存储访问密钥等）。这些值仅用于本地起服务，生产环境必须覆盖。

模型供应商、MinerU 与 You.com 等 API key 通过环境变量注入；仅在对应 provider/功能启用时必须提供。OSS 是对象存储后端，其 access/secret key 由本守卫按 `rag.storage.type=oss` 条件检查。

## 2. Fail-fast 守卫机制

`ProductionCredentialGuard`（`com.nageoffer.ai.ragent.config`）是 Spring Boot 3 `EnvironmentPostProcessor`，通过 `bootstrap/src/main/resources/META-INF/spring.factories` 注册，在应用启动最前置阶段（早于任何 bean 与连接建立）执行：

| 场景 | 行为 |
|:---|:---|
| 未激活任何 profile | 放行（本地直接启动体验不变） |
| active profiles 全部属于 `local` / `dev` / `test` | 放行 |
| active profiles 中存在其他 profile（如 `prod` / `staging`） | 执行全部适用检查 |
| 非开发 profile 下生效值缺失、为空或命中开发默认凭据 | 启动失败 |
| 其他 profile 且占位符无默认值、无法从环境变量解析 | 启动失败 |
| 其他 profile 且 `${KEY:默认值}` 的默认值命中开发默认凭据 | 启动失败 |

失败消息只含配置键名与提示（"请通过环境变量或 secret 覆盖"），不输出任何配置值。

## 3. 生产必须覆盖的键

只要存在非开发 active profile，以下基础凭据必须非空且不得命中开发默认凭据集合；对象存储凭据按当前 `rag.storage.type` 二选一检查：

| 键 | 环境变量注入方式（示例） |
|:---|:---|
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.data.redis.password` | `SPRING_DATA_REDIS_PASSWORD` |
| `rag.storage.s3.access-key` | `RAG_STORAGE_S3_ACCESS_KEY` |
| `rag.storage.s3.secret-key` | `RAG_STORAGE_S3_SECRET_KEY` |
| `rag.storage.oss.access-key` | `RAG_STORAGE_OSS_ACCESS_KEY` 或 `OSS_ACCESS_KEY` |
| `rag.storage.oss.secret-key` | `RAG_STORAGE_OSS_SECRET_KEY` 或 `OSS_SECRET_KEY` |

Spring Boot 的宽松绑定会把环境变量名映射到对应键；也可以使用 `--spring.datasource.password=...` 命令行参数或外部化配置文件。

生产还应根据部署形态覆盖非凭据项（不触发守卫，但影响正确性）：数据源 URL、Redis host/port、S3 endpoint、`rag.vector.type`、`rag.graph.type` 等。

## 4. 启动示例

```bash
# 生产 profile 启动（凭据来自环境变量，示例占位符）
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_USERNAME='<db-user>'
export SPRING_DATASOURCE_PASSWORD='<db-password>'
export SPRING_DATA_REDIS_PASSWORD='<redis-password>'
export RAG_STORAGE_S3_ACCESS_KEY='<access-key>'
export RAG_STORAGE_S3_SECRET_KEY='<secret-key>'
export BAILIAN_API_KEY='<bailian-key>'
export SILICONFLOW_API_KEY='<siliconflow-key>'
./mvnw -B -ntp -pl bootstrap spring-boot:run
```

若任一适用敏感键缺失、为空或仍为开发默认值，启动会立即失败并提示对应键名，不会输出配置值。

## 5. 密钥轮换提醒

守卫只在启动时检查，不参与运行期密钥轮换：

- 轮换采用"先注入新值、验证生效、再下线旧值"的顺序，避免空窗期；
- 环境变量/secret 变更后需重启应用（或在支持动态刷新的场景单独处理）；
- 数据库、Redis、对象存储的凭据轮换建议与各自平台的密钥管理策略配合（Vault、KMS 等），本项目不做配置加密与密钥托管。
