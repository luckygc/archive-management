# 基础设施组件与模块事件边界

本项目优先使用 Spring Boot AutoConfiguration、标准框架 Bean 和组件自身配置，不再维护项目级运行时能力 adapter、统一降级或统一 fail-fast 总装配。业务模块不得直接依赖具体中间件客户端；需要通用基础设施能力时优先依赖成熟框架抽象。

## 当前基础设施入口

| 能力             | 当前入口                       | 配置前缀                     | 说明                                                                                              |
| ---------------- | ------------------------------ | ---------------------------- | ------------------------------------------------------------------------------------------------- |
| HTTP 会话        | Spring Session JDBC            | `spring.session.*`           | 表结构由 Flyway 管理，运行时由 Spring Session 接管。                                              |
| 缓存             | Spring Cache                   | `spring.cache.*`             | 默认 `spring.cache.type=none`；集群部署必须使用共享或分布式 `CacheManager`。                      |
| 调度             | Spring Quartz                  | `spring.quartz.*`            | JDBC JobStore 使用 `spring.quartz.job-store-type=jdbc`；Quartz 原生表保持 `QRTZ_*`。              |
| 模块事件         | Spring Modulith                | `spring.modulith.*`          | 可靠模块事件使用 JDBC Event Publication Registry；事件发布注册表表结构由 Spring Modulith 初始化。 |
| 文件存储         | 项目文件存储服务 + S3 兼容协议 | `archive.storage.*`          | 文件记录落本地数据库，具体对象内容按记录中的存储类型和 bucket 路由。                              |
| 普通用户全文发现 | `FullTextSearchAdapter`        | `archive.search.full-text.*` | 当前内置 PostgreSQL 实现，搜索语义必须同时合并全文、结构化筛选、权限和逻辑删除。                  |

## 最低部署

最低部署是单节点加 PostgreSQL：

```yaml
spring:
    cache:
        type: none
    session:
        jdbc:
            initialize-schema: never
    quartz:
        job-store-type: jdbc
        jdbc:
            initialize-schema: never
    modulith:
        events:
            republish-outstanding-events-on-restart: true
            externalization:
                enabled: false
            jdbc:
                schema-initialization:
                    enabled: true
archive:
    search:
        full-text:
            adapter: postgresql
    storage:
        adapter: LOCAL
        active-local-bucket: local
```

这组配置要求业务数据库、Spring Session JDBC 表、Quartz JDBC 表、项目自有业务表和 Spring Modulith `event_publication` 表可用。Spring Session、Quartz 和 Spring Modulith 的原生表不是项目自有业务表，命名保持上游默认。

## 模块事件

- 模块间业务事件默认使用 Spring Modulith。
- 需要可靠执行的监听器使用 `@ApplicationModuleListener`，由 JDBC Event Publication Registry 记录处理状态，失败或节点异常后可重新发布未完成事件。
- 只在当前进程内即时处理、且可丢或可由主事务重算的动作，才使用普通 Spring 事件或 `@TransactionalEventListener`。
- 跨系统外发使用 Spring Modulith event externalization/outbox 或明确选定的消息中间件，不把外发协议硬塞进模块事件监听器。
- 事件监听器必须按业务主键保持幂等，不依赖本机内存状态。

## 异步后台任务

项目不再维护自研通用数据库队列，不保留 `RuntimeQueue`、`RuntimeMessage`、`am_runtime_job`、`am_queue_job` 或 `archive.queue.*` 配置。导入、导出、AI/OCR、批处理、长耗时可观察任务等后台作业，后续优先选 JobRunr 等成熟组件，再按该组件原生配置和表结构接入。

模块事件注册表不是通用任务队列：它只负责模块事件监听器的可靠发布和完成状态，不承载任务看板、任意节点 worker 消费、人工重试、削峰或复杂调度语义。

## 集群边界

- 纯内存事件只在当前 JVM 内传播，集群下不能作为可靠模块事件机制。
- JDBC Event Publication Registry 共享同一业务数据库时可用于集群，但监听器仍需幂等，并避免依赖节点本地状态。
- 集群部署不得使用本地锁、非共享本地文件存储或本地/禁用缓存承载跨节点一致性需求。
- 若需要分布式锁，先选定成熟开源库并固定具体实现，不重新引入项目级锁 adapter。
- 若需要跨节点后台任务分发，优先接入 JobRunr、消息中间件或工作流引擎，不复活项目自研数据库队列。

## 后续中间件接入方式

- Redis 缓存：优先使用 Spring Boot Cache AutoConfiguration 注册 Spring Cache 兼容 `CacheManager`。
- Redis Session：直接按 Spring Session 和 Boot 自动配置接入，不定义项目 session adapter。
- Quartz 调度：继续按 `spring.quartz.*` 配置使用，不定义项目 scheduler adapter。
- DBOS、Temporal、JobRunr 等工作流或后台作业运行时：优先使用官方 Spring Boot starter、客户端 Bean 和组件自身表结构。
- Kafka、RabbitMQ、NATS JetStream 等消息中间件：优先使用 Spring Cloud Stream、JMS 或官方客户端自动配置；只在明确跨系统或跨进程事件外发时接入。
- Elasticsearch、OpenSearch、Solr、Meilisearch 等全文引擎：扩展 `FullTextSearchAdapter`，但必须提供与 PostgreSQL 实现等价的完整搜索语义，不允许只召回裸 ID 后由业务代码二次过滤。
