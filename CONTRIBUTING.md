# Contributing

## 分支规范

- 功能开发：`feature/<描述>`；缺陷修复：`fix/<描述>`；重构/清理：`chore/<描述>`；自动化相关：`harness/<描述>`。
- 一律通过 PR 合入 `main`，禁止直接 push main（main 受 ruleset 保护，直推会被拒绝）。
- PR 标题使用 conventional commits 风格（如 `fix(rag): ...`、`feat(frontend): ...`、`ci: ...`）。

## 本地开发

后端：

```bash
./mvnw -B -ntp spotless:check   # 格式检查
./mvnw -B -ntp -DskipTests package  # 编译构建
./mvnw -B -ntp test -pl bootstrap -Dtest=<TestClass>  # 单测
```

前端：

```bash
cd frontend
npm ci
npm run lint
npm run build
```

## CI 检查清单（提交前自测）

- [ ] 后端：`spotless:check` 通过、`-DskipTests package` 通过
- [ ] 前端（如有改动）：`npm run build` 通过；`npm run lint` 不新增存量之外的 error（存量 21 个为已知技术债）
- [ ] 无 `git diff --check` 问题
- [ ] 不在 commit 中包含凭据、密钥或本地审计产物

## 上游同步

本 fork 定期同步上游 `nageoffer/ragent` main，完整 SOP 见 [docs/upstream-sync.md](docs/upstream-sync.md)。要点：

- 同步分支统一命名 `sync/upstream-<YYYYMMDD>`，合并 commit 标题 `chore: sync upstream main (<日期>)`。
- 冲突原则：本 fork 的测试基线（surefire `excludedGroups` + `-P integration`、vitest/RTL 配置、CI workflow、AGENTS.md）一律保留 fork 版本；上游业务代码原样并入，不静默修改。
- 合并后必须重新验证：后端 `./mvnw -B -ntp test`、前端 `npm ci && npm run test && npm run build`。
- 上游同步只走 PR（main 受 ruleset 保护），不在本地直接 merge 到 main。

## Review 流程

1. 提交 PR 后，GitHub Actions 自动运行 `backend-maven` 与 `frontend-build-lint`。
2. main 分支 ruleset 要求：CI check 全绿 + 至少 1 人 approve + CODEOWNERS 覆盖文件的 owner 审查（当前 owner 均为 KDB-Wind）。
3. 合并使用 GitHub 的 merge（不要 rebase 直推绕过规则）。
4. 模块 owner 见 `.github/CODEOWNERS`。
