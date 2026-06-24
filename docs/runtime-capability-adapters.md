# 运行时能力与 adapter 矩阵

本项目用统一的运行时能力描述项目自有中间件选择，但默认优先利用 Spring Boot AutoConfiguration、标准框架 Bean 和条件装配自动升级或降级，避免为每个中间件都新增项目配置。能力入口优先复用可信度高的规范或框架接口：缓存使用 Spring Cache，HTTP 会话直接使用 Spring Session，调度使用 Quartz/Spring 调度入口，文件对象存储使用 S3 兼容协议。只有队列、运行时锁、运行时能力标识和全文发现能力标识这类缺少统一项目可用标准的能力，才保留薄项目 adapter；业务模块不直接依赖具体中间件客户端。

## 最低部署

最低部署是单机加数据库：

```yaml
archive:
    runtime:
        topology: single
        database:
            adapter: postgresql
        queue:
            adapter: database
            fallback-adapter: database
            max-attempts: 10
        lock:
            adapter: database
            fallback-adapter: database
        storage:
            local-shared: false
    search:
        full-text:
            adapter: disabled
    storage:
        adapter: local
        active-local-bucket: local
spring:
    cache:
        type: none
    quartz:
        job-store-type: jdbc
```

这组配置只要求业务数据库、Spring Session JDBC 必需表、Quartz JDBC 必需表和项目自有 `am_runtime_*` 表存在。Flyway 负责建表。HTTP Session 不进入 `archive.runtime` 能力模型，统一由 Spring Session 和 `spring.session.*` 配置管理；缓存不进入 `archive.runtime` 能力模型，统一由 Spring Cache 和 `spring.cache.*` 配置管理。管理列表、后台筛选、排序、权限过滤、精确字段筛选和统计查询固定使用数据库语义，不提供可切换 adapter。

## 当前内置 adapter

| 能力     | 内置 adapter                   | 单机 | 集群     | 说明                                                                                                                                    |
| -------- | ------------------------------ | ---- | -------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| 数据库   | `postgresql`                   | 可用 | 可用     | 当前唯一内置数据库 adapter，启动时校验实际数据库产品                                                                                    |
| 全文发现 | `disabled`                     | 可用 | 可用     | 默认关闭全文发现入口                                                                                                                    |
| 全文发现 | `postgresql`                   | 可用 | 可用     | 使用 PostgreSQL 自带 `pg_trgm` 扩展和 GIN 索引，直接合并全文、筛选、权限和逻辑删除条件                                                  |
| 队列     | `database`                     | 可用 | 可用     | 当前唯一内置队列，主目标是单机最低部署；使用 `am_runtime_job`、租约时间和 `for update skip locked` 认领，达到最大尝试次数后进入死信状态 |
| 锁       | `database`                     | 可用 | 可用     | 使用 `am_runtime_lock` 表和租约时间，`RuntimeLockLease.close()` 释放                                                                    |
| 锁       | `local`                        | 可用 | 禁止     | 仅单机可用，集群启动 fail-fast，`RuntimeLockLease.close()` 释放                                                                         |
| 文件存储 | `local`                        | 可用 | 条件可用 | 集群必须使用共享挂载并设置 `archive.runtime.storage.local-shared=true`                                                                  |
| 文件存储 | `s3`/`minio`/`cos`/`oss`/`obs` | 可用 | 可用     | 显式通过 `archive.storage.adapter` 选择，支持 S3 兼容对象存储                                                                           |

## 后续中间件接入方式

新增中间件时优先复用成熟标准接口和 Spring Boot AutoConfiguration；确无合适标准时才新增项目 adapter，不改业务模块。自动升级不能只看 classpath，必须基于显式配置、已注册标准 Bean 或真实连通性判断：

- Redis 缓存：不定义项目 adapter，优先使用 Boot 的 Cache AutoConfiguration 注册 Spring Cache 兼容的 `CacheManager`。默认 `spring.cache.type=none`；本地简单缓存使用 `spring.cache.type=simple`；Redis 缓存按 `spring.cache.type=redis`、`spring.cache.redis.*` 和 `spring.data.redis.*` 配置接入。
- 多级缓存或数据库缓存：优先以 Spring Cache 兼容 `CacheManager` 包装 Caffeine+Redis、JetCache、Redisson 本地缓存、数据库缓存等实现；引入前必须明确缓存对象范围、TTL、失效事件、集群一致性和脏读容忍度。集群部署下，如果 `spring.cache.type` 是 `generic`、`jcache` 或其他无法自动证明共享性的实现，必须确认底层是共享介质并设置 `archive.runtime.cache.shared=true`。
- 数据库：当前只支持 `postgresql`，项目自有 DDL、索引、查询和迁移均按 PostgreSQL 收口，暂不考虑其他数据库 adapter。
- Redis 锁：实现 `RuntimeLockAdapter`，例如 `adapter()` 返回 `redis`。
- NATS JetStream、RabbitMQ、Kafka 或其他 MQ：当前不内置；优先评估 Spring Cloud Stream、JMS 或对应客户端的 Boot AutoConfiguration 能否提供稳定 Bean，再实现 `RuntimeQueueAdapter` 把 `RuntimeMessage` 映射为对应 broker 的 subject/topic、headers 和 body。NATS JetStream 本身不是本项目直接依赖的标准接口。
- Redis Session：不定义项目 adapter，直接按 Spring Session/Boot 自动配置接入；使用 `spring.session.*`、`spring.session.data.redis.*` 和 `spring.data.redis.*` 配置。HTTP Session 存储不要和“Redis 业务缓存”混为一谈。
- DBOS、Temporal 或其他调度/工作流运行时：优先使用对应 Spring Boot AutoConfiguration、官方客户端 Bean 或框架 starter；Quartz 调度直接按 `spring.quartz.*` 配置，不定义项目 scheduler adapter。
- Elasticsearch、OpenSearch、Solr、Meilisearch 等全文引擎：优先利用 Boot 或官方客户端自动配置出的客户端 Bean 识别能力；扩展 `FullTextSearchAdapter` 时必须同时提供普通用户完整搜索上下文的同语义查询入口，在一次搜索语义中合并全文、结构化筛选、权限判断和逻辑删除条件，不允许只返回裸 ID 让业务代码再召回过滤。

## 降级机制

`queue`、`lock` 支持自动或显式 fallback。默认策略是：先让 Spring Boot AutoConfiguration 和标准 Bean 决定可用能力；项目层再按部署拓扑、共享性和依赖资源做 fail-fast。主 adapter 未注册或对应依赖未接入时，可以显式降到基础实现：

- 队列：默认从未接入的外部 MQ 降到 `database`。
- 锁：默认从未接入的外部锁降到 `database`。

降级只处理“可选中间件未接入”这类缺口，不处理危险部署配置。以下情况仍必须 fail-fast：实际数据库不是 PostgreSQL、集群拓扑使用本地锁、集群使用非共享本地文件存储、集群使用禁用/本地/无法确认共享性的缓存、对象存储配置不完整、Quartz JDBC 必需表缺失、Quartz 集群配置缺失、全文检索启用但 `pg_trgm` 或索引缺失。需要禁止降级时，将对应 `fallback-adapter` 配为空字符串。

文件存储不提供自动 fallback：上传位置必须由 `archive.storage.adapter` 显式选择。对象存储配置不完整时启动失败，避免从对象存储静默落到本地目录导致历史文件位置不可预测。

## 业务代码使用约定

- 异步任务只依赖 `RuntimeQueue`。当前内置实现是数据库队列，消息使用 CloudEvents 1.0 风格的 `RuntimeMessage` 薄信封；`complete(...)` 和 `fail(...)` 返回 `false` 表示当前 worker 已丢失租约或任务状态已变化，调用方不得继续假定处理成功。`archive.runtime.queue.max-attempts` 控制最大认领尝试次数，达到上限后任务进入 `dead_letter` 状态，不再被认领。
- 分布式互斥只依赖 `RuntimeLockManager`。拿到 `RuntimeLockLease` 后优先用 try-with-resources 或显式 `close()` 释放；`unlock(lease)` 只是同一释放语义的包装。
- 缓存只依赖 Spring Cache。默认 `spring.cache.type=none` 必须表现为永远 miss；业务逻辑不能因为缓存缺失而改变正确性。集群部署下不得使用 `none`、`simple`、`caffeine`、`cache2k` 这类禁用或本地缓存类型，必须切到 Redis、Hazelcast、Infinispan、Couchbase 等分布式类型，或接入共享 `CacheManager` 并声明 `archive.runtime.cache.shared=true`。
- 全文发现只依赖 `FullTextSearchAdapter` 的能力标识和启动校验。内置 `postgresql` 入口直接用数据库查询同时完成全文、分类、全宗、权限、删除状态和动态字段筛选；外部全文引擎也必须提供等价的完整搜索语义。
- 调度直接按 Spring Quartz 入口使用，不定义项目运行时能力；关闭调度使用 `spring.quartz.auto-startup=false`。

## fail-fast 规则

启动阶段必须拒绝以下配置：

- adapter 名称为空。
- 配置了 adapter，但应用中没有注册对应 Bean。
- `archive.runtime.queue.max-attempts` 小于 1。
- `archive.runtime.database.adapter=postgresql`，但实际连接的数据库不是 PostgreSQL。
- `spring.quartz.job-store-type=jdbc` 且 `spring.quartz.auto-startup=true`，但当前 schema 下缺少 `spring.quartz.properties.org.quartz.jobStore.tablePrefix` 对应的 Quartz 必需表，默认前缀是 `QRTZ_`。
- `topology=cluster` 且 `lock.adapter=local`。
- `topology=cluster` 且 `archive.storage.adapter=local`，但没有声明本地存储是共享挂载。
- `topology=cluster` 且 `archive.storage.adapter` 选择对象存储，但 `archive.storage.object` 配置不完整。
- `topology=cluster` 且 `spring.cache.type` 是 `none`、`simple`、`caffeine`、`cache2k` 等禁用或本地缓存类型。
- `topology=cluster` 且 `spring.cache.type` 是 `generic`、`jcache` 或其他无法自动证明共享性的实现，但未设置 `archive.runtime.cache.shared=true`。
- `topology=cluster` 且启用 Quartz 调度，但没有设置 `spring.quartz.job-store-type=jdbc` 或 `spring.quartz.properties.org.quartz.jobStore.isClustered=true`。
- 全文发现启用 `postgresql`，但数据库缺少 `pg_trgm` 扩展或全文检索 GIN 索引。

这些规则由 `RuntimeCapabilityValidator` 和搜索能力 validator 固化，测试覆盖 Spring 实际装配和单元校验。
