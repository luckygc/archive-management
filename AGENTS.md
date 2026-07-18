# 仓库协作规则

## 协作总原则

- 始终使用中文交流、编写文档和注释。
- 默认按最小闭环推进，不顺手扩展相邻模块。遵守奥卡姆剃刀：优先复用现有框架、组件和数据模型，不为未来可能新增抽象、适配层、配置开关、兼容分支或预留能力。
- 维护性、可排障性、失败与恢复路径、业务不变量优先。服务端校验、权限和数据库约束必须守住边界，前端校验只改善体验，不能替代它们。
- 数据库写入、权限判断、事务边界、远程调用、文件读写、缓存失效和其他状态变更等副作用应集中且可追踪。
- 保护用户工作树；不处理、覆盖、回退或清理与当前任务无关的改动和未跟踪文件。
- 选用组件或库前核对协议与商业化行为；不把 Enterprise/Pro 能力作为默认基础能力，不采用限制商业使用的库。
- 自动化贡献者不得主动启动长期占用端口的开发服务；需要预览时只告知用户可执行的命令。
- 最小必要验证必须通过 `Taskfile.yml`、项目脚本或构建配置中的真实入口执行；治理相关改动必须运行 `task governance-check`。

## 真相源路由

| 改动类型 | 必读真相源/技能 |
| --- | --- |
| 业务、状态机、权限、验收 | 对应 `openspec/specs/` 与活动 change |
| 项目自有 API | `openspec/specs/api-contract/spec.md` + `archive-api-design-strategy` |
| 产品定位 | `PRODUCT.md` |
| 前端界面 | `PRODUCT.md`、`DESIGN.md` + `impeccable` |
| 稳定架构和包边界 | `docs/architecture.md` + ArchUnit |
| 持久化、实体、Repository、Mapper、审计 | `docs/architecture.md` + `archive-persistence-strategy` |
| 开发、验证、部署、运维 | `Taskfile.yml`/构建配置 + 对应 `docs/` |
| OpenSpec 变更 | `openspec/config.yaml` + 对应 OpenSpec 工作流技能 |

发生冲突时，先校准真相源再修改代码。`docs/superpowers/` 是历史资料，不是当前规范，其适用范围只由该目录的 `README.md` 解释。

## 强边界

1. `backend/archive-server/` 是单 Spring Boot 主应用；`app` 只放启动和应用级装配，业务放 `module`，技术适配放 `infrastructure`，`common` 只放跨业务基础约定，不放业务语义或外部技术适配。`backend/preview-service/` 是独立 Go 文件预览服务，不并入主应用进程。
2. `module` 内先按业务子域拆分，再遵守 `web -> service -> manager -> repository/mapper` 依赖方向；`web` 不直连持久化，跨模块优先调用目标模块 Service，不绕过其 Repository、Mapper 或底层表。
3. Service、Manager 和领域协作只有一个实现时直接使用具体类；只有框架声明式合同、稳定基础设施端口或已经存在的多实现策略才使用接口。
4. 同一 Spring Bean 的 public 方法不得调用本类另一 public 方法；共享实现提取为 private 核心方法，独立事务、权限或业务边界拆到另一具体 Bean。禁止 self 注入、通过 `ApplicationContext` 查找本 Bean 或用代理绕过。
5. 项目 SQL 和 Flyway 迁移只面向 PostgreSQL；标识符使用小写 snake_case，不得硬编码 `public` schema。
6. Java 默认使用 JSpecify `@NullMarked`；可空类型使用 `org.jspecify.annotations.Nullable`。Jakarta Data Repository 方法签名按 provider 要求使用 `jakarta.annotation.Nullable` / `jakarta.annotation.Nonnull`。
7. Java 格式和 import 以 Spotless + AOSP `google-java-format` 为唯一真相源，不另行维护手写格式口径。
8. 前端工作区根目录是 `frontend/`；Vite+ 通过该目录的项目脚本或 `pnpm exec vp` 使用。自动化贡献者不得主动启动 dev server，预览只告知用户命令。
9. 后端 Maven 根目录是 `backend/archive-server/`；前端应用和共享包分别位于 `frontend/admin/`、`frontend/packages/core/`。前后端具体命令以 `Taskfile.yml` 和构建配置为准，不在本文件复制命令矩阵。

## 技能与文档查询

- 任务命中项目技能时，先完整读取对应 `SKILL.md`；前端任务还必须先读 `PRODUCT.md` 和 `DESIGN.md`。
- Ant Design、Ant Design Pro、Pro Components 或 `@ant-design/cli` 问题优先查询项目内 Ant Design CLI、本地 `llms.txt` 和已安装文档；仅在缺失或不足时回退官方文档。

## Agent skills

### Issue tracker

本仓库使用 GitHub Issues 跟踪需求、规格和实施任务。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用 `needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human` 和 `wontfix` 五个默认角色标签。详见 `docs/agents/triage-labels.md`。

### Domain docs

采用 single-context 布局：领域上下文位于根 `CONTEXT.md`，架构决策位于 `docs/adr/`。详见 `docs/agents/domain.md`。
