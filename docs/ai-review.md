# AI 辅助审查

本仓库采用单维护者治理模型。AI Review 是辅助质量信号，不是独立人工 approval；最终合并决定始终由维护者作出。

## 第一层：DeepSeek V4 Flash

在 pull request 下由 OWNER/MEMBER/COLLABORATOR 评论：

```text
/deepseek-review
```

也可以在命令后写关注点，但固定审查规则仍然优先：

```text
/deepseek-review 重点检查数据库迁移和回滚路径
```

`.github/workflows/deepseek-review.yml` 固定使用 `opencode-go/deepseek-v4-flash` 和
`max` variant。它没有 `contents: write`，不能推送或合并；OpenCode 工具权限也显式禁止
`bash`、`edit` 和 `write`。结果只是普通评论/COMMENTED review，不计作 approval。

仓库管理员需要在 **Settings → Secrets and variables → Actions** 配置
`OPENCODE_API_KEY`。密钥不得写入 workflow、PR、日志或仓库文件。自动触发未启用，以控制额度并避免外部用户滥用。
工作流同时禁用 `task`（子 agent 委派）和 `webfetch`，防止第一层审查隐式切换到其他付费模型或访问外部网络。

## 第二层：官方 Codex

在高风险改动或准备合并时按需评论：

```text
@codex review
```

在 ChatGPT 的 Codex Code Review 设置中启用该仓库的 **Code review**，但关闭
**Automatic reviews**，也不要设置 Security Review “Whenever code review runs”。Codex Review
不作为 approval 或 required check，不加入 ruleset bypass list。

不要使用 `@codex fix ...`，除非维护者明确希望 Codex 修改 PR 分支；单纯审查只使用
`@codex review`。
