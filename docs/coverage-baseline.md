# 覆盖率基线与递增规则

基线采集日期：2026-08-12。覆盖率用于防止继续下降，不代表当前覆盖充分；新增测试应优先覆盖安全边界、认证、远程抓取、SSE、核心检索与状态变更路径。

## 当前基线

后端使用 JaCoCo 0.8.14。`./mvnw -B -ntp verify` 为每个有代码的模块生成
`target/site/jacoco/`。按四个模块报告的 counter 加权汇总：

| 指标 | 覆盖 | 总计 | 基线 |
| --- | ---: | ---: | ---: |
| Instructions | 15,754 | 69,597 | 22.64% |
| Branches | 1,419 | 7,131 | 19.90% |
| Lines | 3,345 | 15,066 | 22.20% |
| Methods | 671 | 2,611 | 25.70% |
| Classes | 160 | 433 | 36.95% |

后端暂不设置跨模块百分比 check：逐模块阈值会误伤测试分布不均的模块，而当前 reactor
没有独立聚合 coverage 模块。CI 会保存所有模块报告；下一阶段应建立聚合报告后再加 line/branch ratchet。

前端使用 Vitest V8 coverage，当前 18 个测试的全量源码基线为：

| Statements | Branches | Functions | Lines |
| ---: | ---: | ---: | ---: |
| 2.82% | 1.97% | 1.77% | 2.95% |

`vite.config.ts` 的初始硬阈值分别为 2.8 / 1.9 / 1.7 / 2.9，略低于实测值，仅用于阻止回退。不得通过排除正常业务源码来提高数字。

## 运行与调高规则

```bash
./mvnw -B -ntp verify
cd frontend
npm run test:coverage
```

每次新增稳定测试后，在同一个 PR 中附报告并只向上调整阈值；阈值不能下调。CI 会分别保留
`jacoco-reports` 与 `frontend-coverage` artifact 14 天。集成测试覆盖率不计入默认基线，避免外部服务可用性改变门禁结果。
