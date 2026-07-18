## Why

首轮目录重组已经将实现代码按职责归位，但前端工作区、Java 专用工具和本地部署配置仍散落在仓库根目录，根目录职责不够清晰。需要继续把子系统专属配置下沉，同时保持根任务入口、提交钩子、构建和容器流程可重复执行。

## What Changes

- 将 pnpm/Vite+ 前端工作区配置整体移动到 `frontend/`，删除当前未被引用的根 `tsconfig.json`。
- 将 OpenRewrite 配置移动到 `backend/archive-server/`，修正 Maven 插件配置位置。
- 将开发 Compose 配置移动到 `deploy/`，由根 `Taskfile.yml` 显式引用。
- 更新 Dockerfile、编辑器配置、任务入口和当前文档中的相关路径。
- 保留真正跨仓库的 `Taskfile.yml`、`mise.toml`、Git/编辑器基础约定和仓库说明文件在根目录。

### 目标

- 根目录只承载跨项目入口、仓库元数据和顶层说明。
- 前端、后端和部署专属配置分别由对应目录拥有。
- 从根目录执行的正式任务、提交钩子、构建、测试和容器命令继续可用。

### 非目标

- 不改变业务行为、HTTP API、数据库结构或前端交互。
- 不调整 Java、Go、Vue 源码包边界。
- 不为尚不存在的客户端或服务创建占位目录和配置。

## Capabilities

### New Capabilities

无。本变更属于纯工程目录治理，不新增可测试的业务或 API 能力。

### Modified Capabilities

无。现有 OpenSpec 业务与 API 要求不变。

## Impact

- 受影响的工程入口：`Taskfile.yml`、前端工作区配置、前端 Dockerfile、`.vscode/settings.json`、后端 `pom.xml` 和开发 Compose 命令。
- 受影响的真相源：开发与验证入口以 `Taskfile.yml`、各构建配置和对应 `docs/` 为准；业务与 API 规格不变。
- 迁移后直接在仓库根目录运行裸 `pnpm` 或裸 `docker compose` 不再作为正式入口，统一通过根任务或进入对应目录执行。
