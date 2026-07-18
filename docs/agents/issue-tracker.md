# Issue tracker：GitHub

本仓库的需求、PRD 和实施任务使用 `luckygc/archive-management` 的 GitHub Issues 管理。所有操作使用 `gh` CLI。

## 约定

- 创建：`gh issue create --title "..." --body "..."`
- 查看：`gh issue view <number> --comments`
- 列表：`gh issue list --state open --json number,title,body,labels,comments`
- 评论：`gh issue comment <number> --body "..."`
- 添加标签：`gh issue edit <number> --add-label "..."`
- 移除标签：`gh issue edit <number> --remove-label "..."`
- 关闭：`gh issue close <number> --comment "..."`

在仓库目录内运行命令，由 `gh` 根据 Git remote 自动识别仓库。

## Pull requests 作为 triage 请求入口

**PRs as a request surface: no.**

外部 Pull Request 默认不进入 issue triage 状态机。以后如果需要，可以将该值改为 `yes`。

## 技能操作约定

- “发布到 issue tracker”：创建 GitHub Issue。
- “读取相关 ticket”：运行 `gh issue view <number> --comments`，同时读取标签。
- Issue 和 Pull Request 共用编号；编号类型不明确时，先运行 `gh pr view <number>`，失败后再读取 Issue。

## Wayfinding 操作

- Map：使用带 `wayfinder:map` 标签的单个 Issue。
- 子任务：使用 GitHub sub-issue；不可用时，在 Map 任务列表和子 Issue 的 `Part of #<map>` 中维护关系。
- 类型标签：`wayfinder:research`、`wayfinder:prototype`、`wayfinder:grilling`、`wayfinder:task`。
- 阻塞关系：优先使用 GitHub 原生 issue dependencies；不可用时使用 `Blocked by: #<number>`。
- 领取：`gh issue edit <number> --add-assignee @me`。
- 完成：先评论结论，再关闭 Issue，并将上下文链接补充到 Map。
