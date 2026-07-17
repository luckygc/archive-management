# 架构总览

Archive Management 由单 Spring Boot 主应用、PC 前端和独立文件预览服务组成。本文只记录稳定技术边界；业务合同、运行参数和页面实现分别由 OpenSpec、配置文件和源码承担。

## 顶层组件

| 路径 | 稳定职责 |
| --- | --- |
| `server/` | Spring Boot 后端主应用，承载项目 HTTP API、业务模块、认证授权、迁移和基础设施接入 |
| `web/` | Vue 3 + Element Plus PC 管理工作台 |
| `frontend-core/` | 框架无关的 API client、安全验证和共享类型 |
| `preview/` | 独立部署的 Go 文件预览服务，不嵌入主应用进程 |
| `openspec/` | 通用 API 与业务能力合同 |
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

Service、Manager 和领域协作只有一个实现时直接使用具体 Spring Bean；Jakarta Data Repository、MyBatis Mapper、稳定基础设施端口和已有多实现策略保留接口合同。同一 Bean 的 public 方法不得调用本类另一 public 方法：共享实现提取为 private 方法，独立事务、权限或业务边界拆到另一具体 Bean 并通过构造器注入，不使用 self 注入或代理绕过。

## Java 工程约定

- 字符串空白判断统一使用 Apache Commons Lang `StringUtils`；已弃用的 `StringUtils.removeStart` 使用 `Strings.CS.removeStart` 替代。
- 摘要、编码和 Hex 等通用能力优先使用 Apache Commons Codec 等成熟库，不手写通用算法封装。
- 新增或修改业务方法超过 5 个业务参数时，收敛为语义明确的 request、command、condition 等对象；框架回调、简单构造器和少数稳定底层工具方法除外。
- 内部对象只在真实跨边界或复用收益出现时引入，并使用语义明确的 `Command`、`Summary`、`Option`、`TreeNode` 等名称；不在实现层之间机械复制对象，不使用泛化 `DO/BO/VO/DTO/Model/Info` 作为默认分层命名。
- 纯参数或请求校验不包事务；只有原子写入、状态变化、令牌消费、锁定等场景才开启事务。

HTTP 边界类型的命名与拆分以 [`openspec/specs/api-contract/spec.md`](../openspec/specs/api-contract/spec.md) 为准，本节不重复 API DTO 规则。

## 持久化边界

固定项目表使用直接、窄的 Jakarta Data `@Repository`。每个 Repository 只声明当前业务真实需要的方法，并为自定义方法显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL` 等操作；不继承项目级 Repository 基类，不暴露通用 CRUD，也不使用 `save` 或 upsert 语义代替明确的 insert、update、delete 生命周期。

Repository 通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不依赖一级缓存、脏检查或延迟会话生命周期，也不向业务层返回 `Stream`、游标或其他依赖会话生命周期的对象。

以下场景由 MyBatis 承担：

- 动态档案表或动态列，以及必须经过白名单校验的动态标识符。
- 复杂搜索、数据范围连接、认证适配查询和 PostgreSQL 专用 SQL。
- 批处理、导入导出、报表、DDL 和需要显式执行计划的路径。

项目不使用 Spring Data JPA，也不把 `JdbcClient` 作为业务持久化入口。

## 统一审计

`AuditContextProvider` 是持久化写入统一的当前时间和用户来源：时间始终存在，未认证、匿名或无法识别的用户 ID 可以为 `null`。

- Hibernate 无状态会话审计拦截器为固定实体统一维护通用 `created_at`、`updated_at`、`created_by`、`updated_by`。
- MyBatis 审计插件向参数 Map 注入 `_audit`；Mapper XML 必须显式通过 `#{_audit.now}`、`#{_audit.userId}` 引用所需审计值，插件不隐式改写 SQL。
- `deletedBy`、`lockedBy`、`owner`、`requestedBy` 等表达业务动作、归属或责任人的字段继续由业务用例显式维护，不与通用审计字段混为一套来源。

## HTTP API

项目自有 API 的资源建模、URL、HTTP 方法、DTO、分页、过滤、排序、ID、异步任务和 ProblemDetail 错误合同只以 [`openspec/specs/api-contract/spec.md`](../openspec/specs/api-contract/spec.md) 为准。具体业务字段、状态机、权限和验收场景由相应业务 OpenSpec 承担；[`api.md`](api.md) 仅提供使用入口和规格索引。

会话认证由 Spring Security 与 Spring Session 承担，浏览器端状态不能替代服务端认证、授权和数据范围判断。

## 前端边界

`web/` 是 PC 高密度档案工作台，产品方向与 Element Plus 设计系统分别以 [`PRODUCT.md`](../PRODUCT.md) 和 [`DESIGN.md`](../DESIGN.md) 为准。页面使用服务端合同作为数据和权限边界；前端校验、按钮状态和路由可见性只改善体验。

`frontend-core/` 只提供框架无关的共享能力，不承载业务页面或 UI 壳层。具体路由、页面组织和请求流程属于源码实现，不写入稳定架构文档。

## 文件与预览

文件内容只使用 S3 兼容对象存储，业务模块统一通过 `FileStorageService` 使用存储能力。endpoint、bucket、凭证和 path-style 等参数以 [`application.yaml`](../server/src/main/resources/application.yaml) 及部署环境外部配置为准。

`preview/` 作为独立 HTTP 服务部署，与主应用保持进程和重转换依赖隔离。其接口和能力合同以 [`file-preview-service`](../openspec/specs/file-preview-service/spec.md) 及 [`preview/README.md`](../preview/README.md) 为准。

## 运行时基础设施

- Spring Session JDBC 管理 HTTP 会话。
- Spring Cache 是缓存抽象；当前默认 `spring.cache.type=caffeine`，配置真相源为 [`server/src/main/resources/application.yaml`](../server/src/main/resources/application.yaml)，本文不复制 provider 矩阵。
- Spring Quartz 管理调度和 JDBC JobStore。
- Flowable process engine 承担流程能力。
- 审批运行态任务、候选关系、历史和意见以 Flowable 为唯一真相源；项目只保存定义草稿、发布版本和业务实例绑定。`am_unified_todo` 是跨业务、可重建的查询投影，不能替代来源业务的状态与权限校验。
- Spring Modulith 与 JDBC Event Publication Registry 承担可靠模块事件发布。
- Flyway 管理数据库结构迁移和框架原生表。

项目自有表使用 `am_` 前缀；Flowable `ACT_*`、Quartz `QRTZ_*` 和 Spring Session `SPRING_SESSION*` 属于第三方框架原生表，不适用项目自有表命名规则。

这些组件优先使用 Spring Boot AutoConfiguration、标准 Bean 和组件自身配置，不维护项目级会话、缓存、调度 adapter 或自研通用队列。具体开关、表名、线程、端点和超时参数以 `application.yaml`、迁移脚本及相应运维文档为准。

## 工程约束

Java 格式由 Spotless + AOSP `google-java-format` 统一，模块边界由 ArchUnit 固化，PostgreSQL 相关集成测试可使用 Testcontainers。真实开发和验证入口以 [`Taskfile.yml`](../Taskfile.yml) 与 [`development.md`](development.md) 为准。
