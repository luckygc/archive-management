## Why

根目录仍保留三份当前不需要的辅助文档，`tooling/` 又混放前端补丁、跨源码检查和 OpenSpec 治理脚本，目录所有权仍不够明确。继续按消费者归位这些文件，可以让根目录只表达仓库级入口和当前真相源。

## What Changes

- 删除根 `CHANGELOG.md`、`CONTRIBUTING.md` 和 `THIRD_PARTY_NOTICES.md`。
- 将 `cap-widget` 补丁移动到 `frontend/patches/`。
- 将由前端工作区执行的源码行数检查移动到 `frontend/scripts/`。
- 将 OpenSpec 治理检查移动到 `openspec/scripts/`。
- 更新 pnpm、Docker、Taskfile、测试和当前文档中的路径，最终删除空的 `tooling/`。

### 目标

- 根目录不再保存本项目当前不采用的变更日志、贡献说明和第三方声明文件。
- 工具文件与唯一消费者或治理真相源相邻，`tooling/` 不再充当杂项目录。
- 正式任务、提交钩子、前端依赖补丁、容器构建和治理检查继续可重复执行。

### 非目标

- 不改变业务行为、API、数据库、依赖版本或源码行数阈值。
- 不修改 `docs/superpowers/` 中的历史资料以追赶当前目录。
- 不把个人安装的 Codex 技能提交到项目仓库。

## Capabilities

### New Capabilities

无。本变更不新增业务或 API 能力。

### Modified Capabilities

无。现有 OpenSpec 业务规格要求不变。

## Impact

- 受影响的工程入口：`Taskfile.yml`、`frontend/package.json`、`frontend/pnpm-workspace.yaml`、`frontend/admin/Dockerfile`、治理与源码行数测试。
- 受影响的当前文档：根 `README.md` 与 `docs/architecture.md` 的目录说明。
- 受影响的真相源：开发和治理命令仍以 `Taskfile.yml`、构建配置和当前 `docs/` 为准；业务与 API specs 不变。
