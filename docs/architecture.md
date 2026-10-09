# 架构总览

Archive Management 由单 Spring Boot 主应用和 PC 前端组成。本文只记录稳定技术边界；业务验收、运行参数和页面实现分别由能力规格、配置文件和源码承担。

## 顶层组件

| 路径 | 稳定职责 |
| --- | --- |
| `server/` | Spring Boot 后端主应用，承载项目 HTTP API、业务模块、认证授权、迁移和基础设施接入 |
| `frontend/admin/` | Vue 3 + Element Plus PC 管理工作台 |
| `frontend/packages/core/` | 框架无关的 API client、认证请求和共享类型 |
| `specs/` | 当前稳定的 API 与业务能力验收规格 |
| `tasks/` | 进行中变更的规格、计划与任务 |
| `docs/` | 开发、部署、运维、使用和稳定架构说明 |

## 后端包与模块边界

后端 Java 包根为 `github.luckygc.am`：

| 包 | 职责 |
| --- | --- |
| `app` | 启动类、应用级装配和启动期编排，不承载业务逻辑或具体技术适配 |
| `module` | 按业务边界组织的业务模块集合 |
| `infrastructure` | Spring Security、Hibernate、MyBatis、存储、缓存和其他技术适配 |
| `common` | 跨业务模块共享的基础约定，不放业务语义或外部技术适配 |

业务子域内默认依赖方向为：

```text
web -> service -> manager -> repository/mapper
```

Controller 不直接依赖 Repository 或 Mapper。跨模块协作优先调用目标模块已有 Service，不绕过其业务边界操作 Repository、Mapper 或底层表。模块包依赖由 ArchUnit 测试固化。

跨模块用户目录查询通过认证模块 Service 返回不含凭证的用户摘要；用户角色关系由授权模块 Service 维护。认证专属 HTTP 过滤器和结果处理器归入 `module.authentication.web.security`，通用 Spring Security 配置仍属于 `infrastructure.security`。

`archive` 内部同样按子域保持持久化所有权：业务库用例通过条目子域的具体归属 Service 查询引用和修改条目、案卷归属，库状态转换、权限和历史仍由业务库用例编排。规则字段目录通过元数据只读目录 Service 获取真实定义，元数据写入仍调用规则引用保护；只读目录 Service 不反向依赖规则，避免将查询与破坏性变更校验绑定到同一 Bean。

`archive.mapper` 承担共享动态表 SQL，其持久化接口只允许条目、元数据的 Service/Manager 及 Mapper 内部协作调用，查询参数类型可以跨子域使用。ArchUnit 同时检查内部子域跨 Repository/Mapper 访问、共享 Mapper 调用范围和元数据只读 Service 的反向依赖，并保留顶层模块循环检查。

Service、Manager 和领域协作只有一个实现时直接使用具体 Spring Bean；Jakarta Data Repository、MyBatis Mapper、稳定基础设施端口和已有多实现策略保留接口合同。同一 Bean 的 public 方法不得调用本类另一 public 方法：共享实现提取为 private 方法，独立事务、权限或业务边界拆到另一具体 Bean 并通过构造器注入，不使用 self 注入或代理绕过。

## Java 工程约定

- 字符串空白判断统一使用 Apache Commons Lang `StringUtils`；已弃用的 `StringUtils.removeStart` 使用 `Strings.CS.removeStart` 替代。
- 摘要、编码和 Hex 等通用能力优先使用 Apache Commons Codec 等成熟库，不手写通用算法封装。
- 新增或修改业务方法超过 5 个业务参数时，收敛为语义明确的 request、command、condition 等对象；框架回调、简单构造器和少数稳定底层工具方法除外。
- 内部对象只在真实跨边界或复用收益出现时引入，并使用语义明确的 `Command`、`Summary`、`Option`、`TreeNode` 等名称；不在实现层之间机械复制对象，不使用泛化 `DO/BO/VO/DTO/Model/Info` 作为默认分层命名。
- 纯参数或请求校验不包事务；只有原子写入、状态变化、令牌消费、锁定等场景才开启事务。

HTTP 边界类型的命名与拆分以 [`specs/SPEC-项目API合同.md`](../specs/SPEC-项目API合同.md) 为准，本节不重复 API DTO 规则。

## 持久化边界

固定项目表使用直接、窄的 Jakarta Data `@Repository`。每个 Repository 只声明当前业务真实需要的方法，并为自定义方法显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL` 等操作；不继承项目级 Repository 基类，不暴露通用 CRUD，也不使用 `save` 或 upsert 语义代替明确的 insert、update、delete 生命周期。

Service 显式选择创建、修改或删除分支，并持有事务、权限、业务状态及错误映射边界。Repository 不依赖方法名派生操作，也不以模糊写入方法替代明确的生命周期。

Repository 通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不依赖一级缓存、脏检查或延迟会话生命周期，也不向业务层返回 `Stream`、游标或其他依赖会话生命周期的对象。

以下场景由 MyBatis 承担：

- 动态档案表或动态列，以及必须经过白名单校验的动态标识符。
- 复杂搜索、数据范围连接、认证适配查询和 PostgreSQL 专用 SQL。
- 批处理、导入导出、报表、DDL 和需要显式执行计划的路径。

项目不使用 Spring Data JPA，也不把 `JdbcClient` 作为业务持久化入口。

## 统一审计

`AuditContextProvider` 是持久化写入统一的当前时间和用户来源：时间始终存在，未认证、匿名或无法识别的用户 ID 可以为 `null`。

Hibernate 与 MyBatis 均从该 provider 获取审计上下文；Service 不预填通用审计字段，实体也不使用另一套自动时间注解或 listener 填充同一字段。

- Hibernate 无状态会话审计拦截器为固定实体统一维护通用 `created_at`、`updated_at`、`created_by`、`updated_by`。
- MyBatis 审计插件向参数 Map 注入 `_audit`；Mapper XML 必须显式通过 `#{_audit.now}`、`#{_audit.userId}` 引用所需审计值，插件不隐式改写 SQL。
- `deletedBy`、`lockedBy`、`owner`、`requestedBy` 等表达业务动作、归属或责任人的字段继续由业务用例显式维护，不与通用审计字段混为一套来源。

## HTTP API

项目自有 API 的无前缀资源路径、受约束的冒号动作、`camelCase` 字段，以及分页、过滤、排序、ID、异步任务和 ProblemDetail 错误合同，均以 [API 能力规格](../specs/SPEC-项目API合同.md) 为准；其中新接口采用外部规范，存量接口另列兼容合同。设计取舍见 [ADR-0001](adr/0001-project-api-style.md)。具体业务字段、状态机、权限和验收场景由相应业务规格承担；[`api.md`](api.md) 仅提供使用入口和规格索引。

新增顶层 API 资源路径时，须同步核对服务端 `ApiRequestPaths` 和前端开发代理的匹配，确保鉴权、可选请求签名与开发环境同源访问覆盖该路径。PC 使用 hash 路由，页面地址的 `#` 片段不会发送给服务端。

Controller 方法显式声明完整 URL，不通过类级 `@RequestMapping` 与方法级相对路径拼接项目自有 API；冒号动作也不通过相对路径拼接。该约束属于项目内部实现边界，不是外部 HTTP API 规范。

会话认证由 Spring Security 与 Spring Session 承担，浏览器端状态不能替代服务端认证、授权和数据范围判断。

## 运行时约束与规则边界

运行时定义直接携带全宗、分类、档案层级和固定触发点作用域。字段目录由固定字段、当前分类动态字段、实物字段和只读上下文字段实时组成；条件只接受带资源上限和类型校验的结构化 AST。系统通过代码注册固定触发点及 `REJECT`、`WARN`、`SET_FIELD` 动作，配置不能提供 SQL、脚本或任意实现入口。

条目、案卷、文件、导入和导出 Service 在副作用前调用统一执行核心。动作只改变尚未持久化的候选值或返回决策，事务仍由业务 Service 拥有；阻断、配置失效、冲突和执行错误均失败关闭。已发布定义由应用状态机与 PostgreSQL 约束共同保证不可变，追踪查询在数据库内应用权限和数据范围。

## 前端边界

`frontend/admin/` 是 PC 高密度档案工作台，产品方向与 Element Plus 设计系统分别以[项目总规格](../SPEC.md)和[设计系统](design-system.md)为准。页面使用服务端合同作为数据和权限边界；前端校验、按钮状态和路由可见性只改善体验。

`frontend/packages/core/` 只提供框架无关的共享能力，不承载业务页面或 UI 壳层。具体路由、页面组织和请求流程属于源码实现，不写入稳定架构文档。

通用分页合同、查询参数编码、HTTP 错误和会话能力在 `core` 保持唯一实现；管理端业务 API 与请求响应类型由管理端拥有。页面只依赖自己的内部实现和共享能力，多个页面使用的档案查询、字段与结果表格能力归入管理端 `shared/archive`。共享能力不得反向依赖页面或应用壳层，`core` 不依赖管理端或 Vue、Pinia、Element Plus 等 UI 框架。

## 文件存储

文件内容只使用 S3 兼容对象存储，业务模块统一通过 `FileStorageService` 使用存储能力。endpoint、bucket、凭证和 path-style 等参数以 [`application.yaml`](../server/src/main/resources/application.yaml) 及部署环境外部配置为准。

## 运行时基础设施

- Spring Session JDBC 管理 HTTP 会话。
- Spring Cache 是缓存抽象；当前默认 `spring.cache.type=caffeine`，配置真相源为 [`application.yaml`](../server/src/main/resources/application.yaml)，本文不复制 provider 矩阵。
- Spring Quartz 管理调度和 JDBC JobStore。
- 过期数据清理合同保留在 `common.cleanup`，执行编排、日志和 Quartz 装配属于 `infrastructure.runtime`。
- Flowable process engine 承担流程能力。
- 审批运行态任务、候选关系、历史和意见以 Flowable 为唯一真相源；项目只保存定义草稿、发布版本和业务实例绑定。`am_unified_todo` 是跨业务、可重建的查询投影，不能替代来源业务的状态与权限校验。
- Spring Modulith 与 JDBC Event Publication Registry 承担可靠模块事件发布。
- Flyway 管理数据库结构迁移和框架原生表。

项目自有表使用 `am_` 前缀；Flowable `ACT_*`、Quartz `QRTZ_*` 和 Spring Session `SPRING_SESSION*` 属于第三方框架原生表，不适用项目自有表命名规则。

这些组件优先使用 Spring Boot AutoConfiguration、标准 Bean 和组件自身配置，不维护项目级会话、缓存、调度 adapter 或自研通用队列。具体开关、表名、线程、端点和超时参数以 `application.yaml`、迁移脚本及相应运维文档为准。

## 工程约束

Java 格式由 Spotless + AOSP `google-java-format` 统一，模块边界由 ArchUnit 固化，PostgreSQL 相关集成测试可使用 Testcontainers。真实开发和验证入口以 [`mise.toml`](../mise.toml) 与 [`development.md`](development.md) 为准。

跨项目结构检查位于根目录 `scripts/`，使用项目已有 Node.js 运行时。源码有效行数沿用前端 300 行提示、500 行失败，后端 500 行提示、700 行失败的阈值；提示用于检查职责，不能通过放宽阈值或添加排除项绕过失败。前端依赖边界检查纳入 `ready`，后端架构测试纳入 Maven 测试，`mise run verify` 顺序执行项目完整验证并拒绝容器集成测试缺失或跳过；GitHub Actions 调用同一入口。
