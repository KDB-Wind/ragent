# Contributing

## 分支规范

- 功能开发：`feature/<描述>`；缺陷修复：`fix/<描述>`；重构/清理：`chore/<描述>`；自动化相关：`harness/<描述>`。
- 治理约定要求一律通过 PR 合入 `main`，禁止直接 push main；2026-08-12 已通过 GitHub API 验证 ruleset active 且无 bypass actor。
- PR 标题使用 conventional commits 风格（如 `fix(rag): ...`、`feat(frontend): ...`、`ci: ...`）。

## 本地开发

后端：

```bash
./mvnw -B -ntp spotless:check   # 格式检查
./mvnw -B -ntp -DskipTests package  # 编译构建
./mvnw -B -ntp -pl bootstrap -am -P integration -Dtest=QueryRewriteTests -Dsurefire.failIfNoSpecifiedTests=false test  # 集成测试类定向运行
```

前端：

```bash
cd frontend
npm ci
npm run lint
npm run test
npm run test:coverage
npm run build
```

## CI 检查清单（提交前自测）

- [ ] 后端：`spotless:check` 通过、`-DskipTests package` 通过
- [ ] 后端：`./mvnw -B -ntp test` 通过（默认排除 `@Tag("integration")`）
- [ ] 前端（如有改动）：`npm run lint`、`npm run test:coverage`、`npm run build` 均通过；lint 基线保持 0 error / 0 warning
- [ ] 无 `git diff --check` 问题
- [ ] 不在 commit 中包含凭据、密钥或本地审计产物

## 上游同步

本 fork 定期同步上游 `nageoffer/ragent` main，完整 SOP 见 [docs/upstream-sync.md](docs/upstream-sync.md)。要点：

- 同步分支统一命名 `sync/upstream-<YYYYMMDD>`，合并 commit 标题 `chore: sync upstream main (<日期>)`。
- 冲突原则：本 fork 的测试基线（surefire `excludedGroups` + `-P integration`、vitest/RTL 配置、CI workflow、AGENTS.md）一律保留 fork 版本；上游业务代码原样并入，不静默修改。
- 合并后必须重新验证：后端 `./mvnw -B -ntp test`、前端 `npm ci && npm run lint && npm run test:coverage && npm run build`。
- 上游同步按治理约定只走 PR，不在本地直接 merge 到 main；执行前需确认 GitHub 端 main ruleset 已启用。

## Review 流程

1. 提交 PR 后，GitHub Actions 自动运行 `backend-maven` 与 `frontend-build-lint`；CodeQL 与 Dependency Review 按各自 workflow 的触发条件运行。
2. 合并前等待 ruleset 中五个 required checks 全绿。当前是单人维护模式，不强制 approval；高风险 PR 应手动请求 DeepSeek/Codex 建议层审查，维护者仍对最终合并负责。
3. 合并使用 GitHub 的 merge（不要 rebase 直推绕过规则）。
4. 模块 owner 见 `.github/CODEOWNERS`。
