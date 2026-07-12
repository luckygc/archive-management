# Hibernate 审计参考

来源：Context7 `/hibernate/hibernate-orm`，查询时间按本次工作流执行。

## 轻量审计字段

Hibernate 提供实体字段级时间戳生成：

- `@CreationTimestamp`：INSERT 时填充创建时间。
- `@UpdateTimestamp`：INSERT 和 UPDATE 时填充更新时间。
- 文档口径里这两个注解默认使用 JVM 当前时间，而不是数据库时间。
- 如果需要数据库生成时间戳，优先查当前 Hibernate 版本下的 `@CurrentTimestamp` 或 `@Generated` SQL 表达式支持。

项目规则：

- 固定实体表的 `created_at`、`updated_at` 由基础设施层无状态会话审计拦截器填充，避免 `save`/upsert 路径误用 Hibernate insert/update 生成语义。
- `@CreationTimestamp`、`@UpdateTimestamp` 可以保留为显式实体 `insert` / `update` 路径的辅助生成注解，但不要把它们当成 `save`/upsert 或 MyBatis 写入路径的审计真相源。
- 如果同一表由 MyBatis、数据库脚本和 Repository 混合写入，不要只依赖 Hibernate 时间戳；DDL 默认值和 SQL 写入路径也要保持一致。
- `created_by`、`updated_by` 这类操作人字段同样由基础设施层无状态会话审计拦截器从应用用户上下文填充，不由 Hibernate 时间戳注解决定。
- 项目当前不跨时区，实体时间字段仍按仓库规则使用 `LocalDateTime` 和数据库无时区 `timestamp`。

## 操作人字段

Jakarta Persistence/Hibernate 支持实体生命周期回调和实体监听器：

- 可在实体方法或独立 listener class 上使用 `@PrePersist`、`@PreUpdate` 等回调。
- 可在实体方法或独立 listener class 上使用 `@PostPersist`、`@PostUpdate`、`@PostRemove` 等回调，处理保存、更新、删除后的附加动作。
- listener 可通过 `@EntityListeners` 关联到实体。
- Hibernate 文档也提到 entity listener 可注入 CDI bean 并触发生命周期相关事件。
- Jakarta Data 1.1 还提供生命周期事件，例如 `PreInsertEvent`、`PreUpdateEvent`、`PostUpdateEvent` 等，可在 CDI observer 中处理。

当前项目固定实体 Repository 必须走 Hibernate `StatelessSession` / `EntityAgent`。无状态会话支持 Hibernate `Interceptor` 的以下回调：

- `onLoad`
- `onInsert`
- `onUpdate`
- `onUpsert`
- `onDelete`

这些回调适合做无状态写入路径下的统一审计适配，例如读取 Spring Security 上下文填充 `created_by` / `updated_by`，并统一填充 `created_at` / `updated_at`。如果使用 `PostPersist`、`PostUpdate`、`PostRemove` 这类实体生命周期回调，必须先通过测试确认它们在当前 Repository 方法和无状态写入路径中会触发，不能按普通有状态 Session 经验推断。

项目规则：

- `created_at`、`created_by` 只在明确创建语义的 `insert` 路径填充；`updated_at`、`updated_by` 在 `insert` 时缺省填充，并在 `update` 路径覆盖为当前值。
- `save`/upsert 不是明确创建语义；无状态会话 `onUpsert` 只能维护 `updated_at`、`updated_by`，不得覆盖 `created_at`、`created_by`。需要创建语义时必须走显式 `insert`。
- 如果采用 listener/interceptor/event 统一填充，必须先设计“当前认证用户上下文”如何进入该组件，避免在实体中直接依赖 Web/Security API。
- MyBatis 写入路径不会自动触发 Hibernate/Jakarta Data listener；混合持久化入口时，Mapper SQL 必须显式维护 `created_at` / `updated_at` / `created_by` / `updated_by`。
- 业务动作审计仍写业务审计表，不要把 `created_by` / `updated_by` 当作业务操作流水。

示例：

```java
@Column(name = "created_at", nullable = false)
@CreationTimestamp
private LocalDateTime createdAt;

@Column(name = "updated_at", nullable = false)
@UpdateTimestamp
private LocalDateTime updatedAt;
```

## Envers 历史审计

Hibernate Envers 是实体历史版本审计能力，用于记录实体每次变更的历史快照和 revision 信息。

文档要点：

- `@Audited` 可开启实体审计。
- Envers 会生成审计表，常见形态是原实体表名加 `_AUD`。
- 审计表包含 revision 标识和 revision 类型，例如 `REV`、`REVTYPE`。
- 可开启属性级 modified flags，记录某个字段是否在该 revision 中变更。
- `org.hibernate.envers.global_with_modified_flag` 可全局开启属性变更标记；也可用 `@Audited(withModifiedFlag = true)` 在实体或属性上选择性开启。
- modified flags 会为每个被跟踪属性增加布尔列，增加审计表体积；写入性能影响通常较小，但表结构会变宽。
- Envers 可查询“某个属性发生变化”的 revision，例如依赖 modified flag 的 `hasChanged()` / `hasNotChanged()` 条件。

## 使用边界

适合 Envers：

- 固定实体表需要历史版本追踪。
- 需要知道某条实体记录在某个 revision 的完整状态。
- 需要查询哪些 revision 修改了某个固定属性。

不适合 Envers：

- 档案动态表、动态字段和动态 `Map` 结果。
- 业务操作审计，例如“归档、锁定、移交、借阅、回滚”等带业务原因、操作对象和业务语义的流水。
- 已经有明确业务审计表的模块，例如 `am_archive_record_audit`，不应为了框架统一而替换成 Envers。

项目规则：

- 轻量字段审计优先用实体字段 + 应用层主体填充。
- 业务行为审计继续用业务审计表。
- 只有明确需要固定实体历史版本、revision 查询或属性级变更标记时，才考虑 Envers。
- 引入 Envers 前必须先检查依赖、Flyway 表结构、审计表命名、数据增长、归档/清理策略和查询入口。
