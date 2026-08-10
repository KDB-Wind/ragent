# 上游同步 SOP

本文档说明 KDB-Wind/ragent fork 如何与上游 `nageoffer/ragent` 保持同步。本 fork 的 `main` 受 ruleset 保护，所有同步结果一律通过 PR 合入。

## 前置

```bash
git remote add upstream https://github.com/nageoffer/ragent.git   # 一次性
git fetch upstream
```

## 流程

### 1. 创建同步分支

```bash
git checkout -b sync/upstream-$(date +%Y%m%d) origin/main
git merge upstream/main
```

### 2. 冲突处理原则

- **本 fork 保留**：测试基线相关配置与文件——
  - 根 `pom.xml`：surefire `excludedGroups` + `-P integration` profile、Mockito javaagent、`argLine` 默认值
  - `frontend/package.json` / lockfile：vitest、@testing-library/react、msw、jsdom 等测试 devDeps（与上游依赖合并不删除）
  - `frontend/vite.config.ts`：vitest `test` 字段（保留上游 IPv4 绑定）
  - `.github/workflows/ci.yml`、`AGENTS.md`、`CONTRIBUTING.md`
- **上游并入**：上游业务代码与文档原样合并，不静默修改。若上游新代码导致本 fork 测试失败，先判断根因：
  1. 本 fork 配置/测试基线问题 → 修复；
  2. 上游自身问题（在纯上游提交上可复现）→ 做最小兼容修复并在 PR 中披露，不改上游业务代码。
- 处理 lockfile 冲突时使用临时文件辅助对比，**用完必须删除**，不得提交（曾出现 `tmp_ours_pkg.json` 误提交，靠 `git commit --amend` 清理）。

### 3. 合并后检查

```bash
# 残留文件与冲突标记
git ls-files | Select-String 'tmp_|\.orig$|\.rej$|\.bak$'
git grep -n '^<<<<<<<\|^>>>>>>>\|^=======$' -- '*.java' '*.ts' '*.tsx' '*.xml' '*.yaml' '*.yml' '*.json' ':!node_modules'
```

抽查关键文件：`pom.xml` 的 excludedGroups/integration profile、`.github/workflows/ci.yml`、`AGENTS.md`（fork 版）、被 `@Tag("integration")` 的测试类是否仍存在（上游可能移动/删除，缺失需评估）、`frontend/vite.config.ts`（vitest 字段 + IPv4 绑定）、`frontend/package.json`（test scripts + devDeps）。

### 4. 本地验证（必须全绿）

```bash
./mvnw -B -ntp test            # 后端默认集合（@Tag("integration") 被排除）
cd frontend && npm ci          # 用合并后新 lockfile
npm run test                   # Vitest 全绿
npm run build                  # 构建通过
npm run lint                   # 记录结果；上游新增 lint error 属上游问题，记录不阻塞
```

若后端默认集合因上游新增依赖外部服务的测试类而失败：先读代码确认依赖外部服务，补 `@Tag("integration")` 隔离（纯增量）。

### 5. 提交与 PR

```bash
git push origin sync/upstream-<日期>
gh pr create --base main --head sync/upstream-<日期> \
  --title "chore: sync upstream main (<日期>)"
```

PR 正文必含：同步范围、冲突处理摘要（含临时文件清理说明）、本地验证结果、上游遗留问题清单、非目标范围、风险提示（如 SQL 升级）。

### 6. 合入

等待 `backend-maven` 与 `frontend-build-lint` 两个 job 全绿，经 approve 后由 GitHub merge 合入。失败按第 2/4 步规则修复后重新 push。

## v1.1.0 SQL 升级提示

- 上游升级脚本位于 `resources/database/upgrades/v1.1.0/`（当前含 10 个脚本：知识库 chunk 日志、消息 thinking、向量集合、变更日志、消息 sources、推荐上下文、意图多集合、ingestion kernel、agent profile 等）。
- 同步 PR 只带入脚本，**不自动执行**。部署升级前：
  1. **备份数据库**；
  2. 按文件名日期顺序执行脚本（2603xx → 2608xx）；
  3. 每条脚本先读注释确认影响范围。
