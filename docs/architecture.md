# 架构总览

Archive Management 当前按单 Spring Boot 主应用起步，配套 PC 前端和独立文件预览服务。架构目标是让档案业务能力、基础设施适配和前端工作台边界清楚，避免在未出现真实需求前引入额外平台层。

## 顶层目录

| 路径 | 职责 |
| --- | --- |
| `server/` | Spring Boot 后端主应用，承载 HTTP API、业务模块、迁移、认证、权限和文件存储适配 |
| `web/` | PC 主应用，Vue 3 + Element Plus 管理界面 |
| `frontend-core/` | 框架无关的前端共享基础能力，包含 Axios API client、CAP 校验和共享类型 |
| `preview/` | 独立 Go 文件预览服务 |
| `openspec/` | API 和业务能力合同 |
| `docs/` | 工程、使用、部署、运维和行业知识文档 |

## 后端分层

后端 Java 包根为 `github.luckygc.am`。

| 包 | 职责 |
| --- | --- |
| `app` | 应用启动类、应用级装配和启动期编排 |
| `module` | 业务模块集合 |
| `infrastructure` | Spring Security、Hibernate、MyBatis、Flyway、缓存、存储、Web 基础设施等技术适配 |
| `common` | 跨业务模块共享的基础约定，不放认证、用户、权限、档案等业务语义 |

业务模块位于 `server/src/main/java/github/luckygc/am/module`：

| 模块 | 当前职责 |
| --- | --- |
| `archive` | 档案元数据、档案条目、案卷、电子文件、导入导出、搜索、治理、本体、规则和数据范围 |
| `authentication` | CAP、登录、会话、用户、登录失败限制和认证审计 |
| `authorization` | 角色和功能权限 |
| `intake` | 归档接收入口 |
| `organization` | 组织部门 |
| `storage` | 文件元数据、短链和下载入口 |

模块内默认依赖方向：

```text
web -> service -> manager -> repository/mapper
```

Controller 不直接依赖 Repository 或 Mapper。跨业务模块复用能力时优先依赖目标模块已有 Service，不绕过目标模块操作底层表。

Spring Bean 的 public 方法表示可由 Controller、事件监听器或其他 Bean 调用的业务入口。本 Bean 内部共享逻辑使用 private 方法；跨事务、权限或业务边界的协作通过构造器注入另一个具体 Bean。项目不使用 Bean 自调用来复用 public 方法，也不为单实现业务能力预留接口。Jakarta Data Repository、MyBatis Mapper、稳定基础设施端口和已有多实现策略继续使用接口合同。

## 持久化边界

项目当前持久化入口是 Jakarta Data Repository 和 MyBatis：

| 场景 | 入口 |
| --- | --- |
| 固定 CRUD 表 | Jakarta Data Repository |
| 动态表、复杂 SQL、批处理、报表、认证适配查询 | MyBatis |
| 文件内容 | `FileStorageService`，统一使用 S3 兼容对象存储 |

固定实体的 `created_by`、`updated_by` 由无状态 Hibernate 会话上的安全审计拦截器统一维护，Service 不再重复赋值。MyBatis 写入不会经过该拦截器，仍须在 SQL 或调用条件中显式维护审计字段。`ArchiveRuleTrace.createdBy` 表示规则执行请求中的业务操作人，`StorageObject.createdBy` 与 `FileLink.createdBy` 同时参与文件所有权和链接归属判断，因此保留显式赋值；这些字段不是固定实体通用审计的第二套来源。

档案 PC 闭环中的具体选择保持单一且可追踪：

- 案卷是固定实体，列表使用 `ArchiveVolumeDataRepository` 和 Jakarta Data `CursoredPage`；Service 在事务内转换为项目游标响应。
- 档案动态字段查询、关系分页、明细行和工作台聚合需要动态表、复杂连接或数据范围 SQL，继续由 MyBatis 承担。
- 关系和工作台在 SQL 内应用数据范围，不能先分页或聚合后再由 Service 过滤。
- 明细行的动态表名和列名只来自已校验元数据，值通过参数绑定；逻辑删除、审计字段和时间字段由 MyBatis 写入路径显式维护。

约束：

- 不使用 Spring Data JPA。
- 不把 JdbcClient 作为项目持久化入口。
- 固定实体写入使用语义明确的 insert、update、delete 方法，不使用 Repository save 或 upsert 语义。
- Jakarta Data Repository 通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不依赖一级缓存、脏检查或延迟会话生命周期。
- Repository 不返回 Stream、游标等依赖会话生命周期的对象。

## 数据模型

档案实例数据分为：

- 固定主表：保存全宗、分类、档号、层级、治理版本、状态、锁定、审计等稳定字段。
- 分类动态表：保存不同档案分类的动态字段。
- 明细表：保存档案条目的重复明细行。
- 文件组件表：保存档案电子文件和短链相关记录。
- 治理表：保存治理方案、版本、适用范围、本体、规则和绑定关系。

本体和规则解释语义与行为，不替代档案主数据存储。

## HTTP API

项目自有 API 统一使用 `/api/v1/**`。通用 API 风格、分页、错误响应、ID 合同和异步任务口径以 `openspec/specs/api-contract/spec.md` 为准。

当前实现中：

- 普通资源使用标准 HTTP 方法。
- 标准 CRUD 难以表达的动作使用 Google AIP-136 custom method 形式，例如 `POST /api/v1/archive-rules/{ruleId}:publish`。
- 错误响应使用 Spring `ProblemDetail` / RFC 9457，并追加 `code`、`reason`、`traceId`、`path` 和 `fieldViolations`。
- 会话认证使用 Spring Security + Spring Session JDBC。

## 前端结构

`web/` 是 PC 管理界面，核心约束来自 `PRODUCT.md` 和 `DESIGN.md`。

主要技术：

- Vue 3 + TypeScript。
- Element Plus。
- Vue Router。
- Pinia，仅承载登录态、权限摘要和页签等全局客户端状态。
- Axios API client。
- Zod。
- Vitest + Vue Testing Library。
- Vite+ 工具链。

页面路由由 `web/src/app/routes.ts` 定义，业务页面放在 `web/src/pages`。菜单递归渲染路由树，面包屑使用匹配路由链，页签标题和缓存策略也来自同一路由树。路由元数据使用 `permission` 表达单一必需权限，使用 `permissionsAnyOf` 表达聚合页的任一能力；菜单、守卫和页签清理共享同一判断。权限摘要以带版本、校验时间和五分钟有效期的深度只读快照原子提交；AppShell 作为唯一调度点，每 60 秒及 focus/visible 时检查并在最后 60 秒预刷新，还按当前 `validUntil` 维护单个可取消 timeout，续期重排、重置和卸载清理。路由守卫在受保护导航前确保快照有效；到期 timeout 或恢复 visible 会先同步提交过期状态，因此 interval 错相和请求节流不会延迟失败关闭。有效期届满且刷新失败时显示权限校验错误，不进入 403 流程。撤权时立即隐藏当前无权 `KeepAlive` 内容并清理其他无权页签，当前页签只在最新 403 导航成功后移除；导航失败时显示内联 403，离开后清理被保留的无权页签。页面服务端数据和表单状态默认保留在页面组件内；当查询、编辑、电子文件、审计、适用范围或绑定已经形成稳定业务闭环时，使用页面同目录的组件或 composable 集中该区域的状态和副作用，不引入通用 CRUD 层或第二套状态源。页签按 `fullPath` 区分同组件多实例，通过只承载 VNode slot 的实例包装组件获得独立缓存名称，由打开页签计算 `KeepAlive include`，并以 `version` key 执行单页签刷新。

当前档案管理页复用高级查询、结果表格和动态字段组件，并由 `useArchiveItemResources` 集中电子文件与审计抽屉的加载、上传、下载和解绑状态；治理页由 `useArchiveGovernanceWorkbench` 集中版本适用范围、装配绑定和默认解析状态。其余大页面已按职责检查，分类、本体、授权、数据范围和用户页面当前仍是单一强关联工作台，暂不为减少文件行数机械拆分。

档案管理和全文发现分别显式维护自己的草稿条件、已提交查询、`orderBy`、页大小、当前游标和响应游标。提交筛选、修改排序或页大小会清除旧游标；翻页、刷新和生命周期操作后的刷新只重放已提交查询，不读取尚未提交的表单草稿。案卷页维护草稿条件、已提交筛选、`limit`、当前游标和响应游标，排序由服务端固定为 `createdAt DESC, id DESC`，前端不维护 `orderBy` 状态。不同业务查询不共享参数化 loader，也不增加第二套列表状态源。

读取失败由所属页面保存失败请求快照并原位恢复。共享 `RequestErrorState` 只展示错误和发出重试事件，不发请求也不持有页面状态；普通失败重放原请求，结构化 `fieldViolations.cursor` 表示游标失效时保留旧结果和查询上下文、清除分页链接，并从相同查询第一页重试。错误反馈保留服务端 `traceId`，字段错误继续回填对应表单，保存、锁定和删除等瞬时命令继续使用消息反馈。

工作台通过 `GET /api/v1/workspace-summary` 读取真实聚合。无档案读取权限的已认证用户获得全零摘要；有权限时后端按启用分类复用档案查询的数据范围和逻辑删除语义汇总，前端不维护演示统计或虚构待办。

`frontend-core/` 不承载页面、UI 壳层或框架 Store，只提供框架无关的共享基础能力。

## 文件预览服务

`preview/` 是独立 Go module，运行时作为单独 HTTP 服务部署。它不嵌入 Spring Boot 主进程，避免把 Office、音视频、CAD 等重转换依赖带入主应用。

当前能力：

- `GET /healthz`
- `GET /v1/capabilities`
- `POST /v1/preview:convert`

首版同步转换只覆盖低风险闭环，复杂格式返回明确不支持或外部工具缺失。

当前 PC 电子文件 Drawer 尚未接入该服务，因此“预览服务可运行”和“产品文件预览闭环”是两个独立状态；后者仍需单独的 OpenSpec 与端到端验收。

## 运行时基础设施

| 能力 | 使用方式 |
| --- | --- |
| 会话 | Spring Session JDBC，表名 `SPRING_SESSION` |
| 缓存 | Spring Cache，默认 Caffeine |
| 调度 | Spring Quartz JDBC JobStore，表名 `QRTZ_*` |
| 模块事件 | Spring Modulith JDBC Event Publication Registry |
| 流程 | Flowable process engine，禁用 IDM 和 Event Registry |
| 数据库迁移 | Flyway |
| 文件存储 | 本地文件系统或 S3 兼容对象存储 |

基础设施优先使用 Spring Boot AutoConfiguration 和框架标准 Bean，不维护项目级统一 adapter 或自研队列。

## 测试和约束

后端：

- ArchUnit 固化模块边界。
- Maven Enforcer 要求 JDK 25。
- Spotless + google-java-format AOSP 风格统一格式。
- Testcontainers 用于 PostgreSQL 相关测试。

前端：

- `pnpm check` 执行格式、lint 和类型检查。
- `pnpm test` 执行前端测试。
- `pnpm build` 执行构建。

预览服务：

- `go test ./...`。
- `go build ./cmd/preview-service`。
