## Context

仓库当前把 `server/`、`preview/`、`web/`、`frontend-core/`、`scripts/` 和 `patches/` 平铺在根目录。组件本身边界明确，但顶层同时暴露可部署应用、共享包和工程支撑内容，无法从路径快速判断职责类别。根构建配置、容器构建和文档又直接引用这些路径，因此迁移必须作为一次跨组件的原子工程变更完成。

现有业务变更可以继续保留原历史描述；本变更只更新当前生效的构建入口、规格和文档，不批量改写 `openspec/changes/archive/` 与 `docs/superpowers/`。

## Goals / Non-Goals

**Goals:**

- 使用 `backend/` 聚合后端可部署服务，并以业务职责命名服务目录。
- 使用 `frontend/` 聚合前端应用、前端共享包和前端工作区测试设置。
- 使用 `tooling/` 聚合仓库治理脚本与依赖补丁。
- 让每个可部署应用拥有自己的构建描述，同时保留根任务作为统一验证入口。
- 更新所有当前有效路径引用，并通过真实构建和治理入口证明迁移后行为不变。

**Non-Goals:**

- 不调整 Java 包、模块依赖、前端包名、Go 包、HTTP API、数据库结构和运行时端口。
- 不拆分或合并现有运行进程。
- 不创建没有真实实现的 Mobile、Portal、Rust、Contracts 或 Kubernetes 占位目录。
- 不重写历史 OpenSpec 归档和 `docs/superpowers/`。

## Decisions

### 按职责和部署单元命名二级目录

目标映射如下：

| 当前路径 | 目标路径 | 职责 |
| --- | --- | --- |
| `server/` | `backend/archive-server/` | Spring Boot 核心业务服务 |
| `preview/` | `backend/preview-service/` | Go 文件预览服务 |
| `web/` | `frontend/admin/` | PC 管理工作台 |
| `frontend-core/` | `frontend/packages/core/` | 前端共享核心 |
| `test/` | `frontend/test/` | 前端工作区测试设置 |
| `scripts/` | `tooling/scripts/` | 仓库治理脚本 |
| `patches/` | `tooling/patches/` | PNPM 依赖补丁 |

不采用 `backend/java/`、`backend/go/` 等语言目录，因为语言已由 `pom.xml`、`go.mod` 和构建文件表达，职责名称更能稳定描述组件边界。替代方案 `apps/` 与 `packages/` 可以减少前后端分类，但会把服务、页面应用和共享包再次平铺到同一类别，不符合本次降低认知噪声的目标。

### 容器构建定义归属应用

Spring Boot 主应用、管理前端和预览服务分别在自己的目录拥有 `Dockerfile`。构建上下文仍为仓库根目录，以便管理前端访问 PNPM workspace 和共享包；部署目录只保存 Nginx 等部署配置。替代方案是保留根级多目标 Dockerfile，但它继续把两个应用的构建生命周期耦合在一个文件中，且不能从应用目录发现其容器入口。

### 根配置只承担工作区编排

`Taskfile.yml`、`package.json`、`pnpm-workspace.yaml`、`vite.config.ts`、`compose.yaml` 和版本工具配置继续留在根目录。它们天然负责整个工作区，迁入任一类别会制造反向依赖或降低工具默认发现能力。

### 当前真相源更新，历史资料保留

当前 `AGENTS.md`、README、架构/开发/部署文档和现行 OpenSpec 规格更新为新路径。历史变更和历史设计保留原路径，以继续准确记录当时状态。全仓残留扫描需要区分当前文件与历史材料，不能用无差别替换破坏历史。

## Risks / Trade-offs

- [路径迁移导致构建入口遗漏] → 对 Taskfile、PNPM、Vite、Docker、忽略规则和治理脚本逐项建立任务，并在迁移后扫描当前文件中的旧路径。
- [移动目录后 PNPM 的本地链接失效] → 使用项目真实安装入口刷新 workspace 链接和锁文件，再执行前端完整检查、测试和构建。
- [被忽略的日志、target、dist 和 node_modules 随目录移动后产生噪声] → 保留其忽略状态，不提交生成物；验证新路径均被 Git 忽略。
- [活动业务变更仍引用旧路径] → 只修改当前仍在进行且其任务真实依赖目录位置的引用；归档历史不改写，并在最终审计中单独列出允许残留范围。
- [容器 Dockerfile 移位后默认构建命令变化] → 在 Taskfile 和部署文档提供唯一真实入口，容器验证显式使用各应用 Dockerfile。

## Migration Plan

1. 创建目录结构变更制品并验证其可应用。
2. 使用 Git 感知的移动操作迁移现有目录，不复制或重建业务源码。
3. 同步更新工作区、任务、容器、治理脚本和当前文档路径。
4. 刷新 PNPM workspace，并运行治理、前端、Java、Go 和容器构建验证。
5. 扫描当前生效文件中的旧路径与生成物跟踪状态，确认只剩明确允许的历史引用。

本变更没有数据库或线上数据迁移。失败时可以通过 Git 反向提交整体回滚目录与配置；不应只回退部分目录，否则构建入口会与源码位置不一致。

## Open Questions

无。尚不存在的组件在真正引入时再创建对应职责目录。
