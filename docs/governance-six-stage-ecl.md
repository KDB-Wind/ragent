# ECL：六阶段治理收尾

## 目标与边界

- 按依赖安全、CodeQL、配置质量、供应链、集成 workflow、诊断与覆盖率的顺序闭合仓库内工作。
- 不写入真实 secret，不直接修改 GitHub ruleset，不把真实集成环境或告警关闭状态假定为成功。
- 安全升级采用可复现 lockfile；行为敏感修改必须有回归测试；门禁先建立可运行基线，再设置可证明的阈值。

## 冻结决策

- Node 最低版本为 20.19，满足 Vite 8 要求。
- npm 阶段验收：`npm ci` 可复现，Critical/High audit 为零，test/lint/build 全绿。
- 远程 URL：只允许 HTTP(S)，阻断本机、私网、链路本地、保留/多播地址；重定向同样重新校验。确需内网来源时只能通过显式主机 allowlist 放行。
- LightRAG base URL 是管理员配置边界；启动时解析固定 authority，用户输入只能进入编码后的 query 参数，不能改变 scheme/host/port。
- 密码新写入统一使用 BCrypt；遗留明文只允许在一次成功校验后原位升级，失败消息保持一致。
- SBOM/SCA 和手动集成 workflow 只生成/引用脱敏产物与命名 secrets。

## 阶段证据

### 1. 前端 Critical/High 依赖

- Vite/Vitest/React plugin 成组升级，Axios/PostCSS 升至修复版本。
- `npm ci` 通过；`npm audit --audit-level=high` 通过，仅余 5 个 Moderate。
- Vitest 18/18、lint、TypeScript project build、Vite build 通过。

### 2. CodeQL Critical/High

- 认证密码改为 BCrypt，新写入统一哈希；仅在遗留明文成功校验后迁移，认证失败信息不区分原因。
- 远程抓取增加 DNS/重定向全链路 SSRF 策略；LightRAG 固定管理员配置的 authority。
- CSV Markdown 转换补反斜杠与管道组合转义。
- 18 个定向安全测试和后端默认 248 个测试通过。GitHub CodeQL 重新扫描与告警关闭状态未验证。

### 3. 配置质量

- CODEOWNERS 路径与实际模块校准；Maven compiler/surefire 插件固定版本。
- 所有 workflow action 固定 40 位 SHA；Dependency Review 权限缩至 `contents: read`。
- 删除 Vite 遗留编译配置，TypeScript 配置改为 `noEmit`；Actionlint 全量通过。

### 4. SBOM/SCA

- Maven 与 npm 均生成 CycloneDX 1.6 JSON；本地分别得到 382/359 个 components。
- `supply-chain.yml` 每周、手动和 release tag 运行，按两份 SBOM 分别生成并上传 Trivy SARIF。
- 本地 Trivy：frontend High/Critical 为 0；backend 为 60 个存量结果。扫描保持 signal-only，PR 新增 High/Critical 由 Dependency Review 阻断。

### 5. 受保护集成测试

- 新增只允许 `workflow_dispatch` 的 `integration.yml`，绑定受保护 Environment 和专用 self-hosted runner 标签。
- 缺少配置只报告键名；真实 secrets、Environment 审批、runner 和运行结果均未在仓库内验证。

### 6. 诊断、覆盖率与安全回归

- SSE 脚本记录 headers/raw stream/stderr、请求关联 ID、curl 状态和 `data`/`done` 业务断言；Git Bash 语法检查通过，真实服务端到端运行未验证。
- JaCoCo 报告已接入 Maven verify；后端加权 line baseline 为 22.20%。
- 前端 V8 coverage 实测 statements/branches/functions/lines 为 2.82/1.97/1.77/2.95%，硬阈值从 2.8/1.9/1.7/2.9 起步。
- CI 上传两端 coverage artifacts；最终 Maven verify、18 个前端测试、coverage、lint、tsc、build、npm High gate 和 Actionlint 均通过。

## 重新进入约束设计的触发条件

- 修复要求改变允许访问的业务网络范围、认证兼容策略或发布产物格式。
- 新门禁无法在干净 runner 上复现。
- 覆盖率阈值只能依赖排除业务代码才能通过。
- 外部 secret、基础设施或 GitHub 权限成为完成仓库内实现的前置条件。
