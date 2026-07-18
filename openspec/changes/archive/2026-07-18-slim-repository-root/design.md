## Context

仓库已经按 `backend/`、`frontend/`、`tooling/` 和 `deploy/` 划分实现，但根目录仍是 pnpm/Vite+ 工作区根，并保存 Java OpenRewrite 与开发 Compose 配置。这样既削弱目录所有权，也使后端专属配置在移动后出现路径漂移风险；当前 Maven 的 `rewrite.yml` 引用已经指向不存在的 `backend/rewrite.yml`。

本变更跨越前端工作区、Maven、Docker、Compose、编辑器和根任务入口。根 `Taskfile.yml` 继续作为使用者从仓库根执行命令的稳定门面。

## Goals / Non-Goals

**Goals:**

- 让前端工作区的包管理、Vite+、锁文件和 TypeScript 配置由 `frontend/` 拥有。
- 让 Java 与本地部署专属配置分别由后端模块和 `deploy/` 拥有。
- 保持根任务、提交钩子、依赖锁定、测试、构建和 Docker 镜像行为可复现。
- 删除无消费者的配置，而不是机械移动无效文件。

**Non-Goals:**

- 不改变业务代码、运行时接口、依赖版本或产品行为。
- 不改变 `backend/archive-server/`、`backend/preview-service/`、`frontend/admin/` 和 `frontend/packages/core/` 的源码边界。
- 不把工具要求在仓库根发现的 Git、Docker 上下文和顶层协作文件强行下沉。

## Decisions

### 前端工作区整体下沉

将 `package.json`、`pnpm-lock.yaml`、`pnpm-workspace.yaml` 和根 Vite+ 配置一起移动到 `frontend/`。工作区包路径相应改为 `admin` 与 `packages/core`，补丁路径改为相对仓库根 `tooling/patches/` 的新位置。根任务通过任务级工作目录执行 pnpm，不要求开发者记忆额外参数。

替代方案是只移动 `vite.config.ts`，但这会让包管理根和工具配置根分离，继续产生含糊的所有权，因此不采用。

### 删除未使用的根 TypeScript 配置

根 `tsconfig.json` 没有被任何子项目继承，且其 `include` 指向不存在的根 `src/`，因此直接删除。`admin` 与 `core` 保留各自现有配置，不为了减少重复引入新的基础配置层。

替代方案是创建 `frontend/tsconfig.base.json` 并改写子项目继承关系，但当前没有足够收益支撑额外抽象。

### 专属配置归属模块

将 `rewrite.yml` 移到 `backend/archive-server/rewrite.yml`，Maven 使用模块内路径；将 `compose.yaml` 移到 `deploy/compose.dev.yaml`，根任务显式传入 Compose 文件。这样配置与消费者相邻，同时根任务仍是统一入口。

保留 `.dockerignore` 在根目录，因为三份镜像继续以仓库根作为 Docker build context；保留 `Taskfile.yml` 与 `mise.toml`，因为它们协调多个技术栈。

### 提交钩子归前端工作区拥有

前端 `prepare` 仍运行 `vp config`，生成目录位于 `frontend/.vite-hooks/`，Git 的 `core.hooksPath` 指向其内部入口。Vite+ 默认从 Git 根执行钩子，无法自动发现下沉后的配置，因此保留一份可版本化的 `frontend/.vite-hooks/pre-commit`，用 `vp staged --cwd frontend` 显式选择前端工作区；生成的内部包装文件继续忽略。

## Risks / Trade-offs

- [风险] pnpm 工作区根改变后，锁文件补丁路径或包筛选范围错误 → 使用冻结锁文件安装、前端完整测试和生产构建验证。
- [风险] 提交钩子生成位置改变 → 检查 `git config core.hooksPath`，执行实际暂存检查并确认构建后工作树干净。
- [风险] Docker 构建上下文中的 COPY 路径失效 → 分别构建管理端、Java 服务和预览服务镜像。
- [风险] Compose 默认发现不再适用 → 根任务始终显式传入 `deploy/compose.dev.yaml`，文档不再推荐裸 `docker compose`。
- [权衡] 从根直接运行裸 pnpm 命令不再工作 → 以更清晰的目录所有权换取这一点，并保留根 Task 入口。

## Migration Plan

1. 先移动配置并更新所有静态引用。
2. 重新安装前端依赖，确认锁文件和补丁解析不发生版本漂移。
3. 验证根任务、提交钩子、OpenRewrite 配置解析、Compose 配置和三份 Dockerfile。
4. 运行完整治理与构建测试后归档工程变更。

回滚时恢复移动前路径和引用即可；本变更不产生数据迁移或运行时状态变更。

## Open Questions

无。根目录保留项以“跨技术栈、工具默认发现或顶层协作”作为明确判定标准。
