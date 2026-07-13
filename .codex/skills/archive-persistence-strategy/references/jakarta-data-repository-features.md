# Jakarta Data Repository 功能面参考

## 接口选择

- 新增固定实体仓库默认从 `CrudRepository<Entity, Id>` 起步。
- `CrudRepository` 暴露的方法比 `BasicRepository` 更多，更适合当前项目固定实体默认仓库接口；只有明确需要收窄公开面时，才退回 `BasicRepository` 或自定义更小接口。
- 只需要少量自定义生命周期/查询方法时，可以直接定义 `@Repository` 接口并使用 Jakarta Data 注解方法。
- 不要因为能继承更多基础接口就暴露更多能力。Repository 公开面应服务模块 Service 的真实用例。

## 方法类型

Jakarta Data Repository 方法大致分为：

- 基础接口方法：例如 `CrudRepository` 提供的保存、按 id 查询、列表、删除、显式更新等固定实体生命周期能力。
- 注解查询方法：`@Find`、`@Query`、`@OrderBy`、`@By`、`@Is` 等。
- 注解生命周期方法：`@Insert`、`@Update`、`@Delete`、`@Save` 等。
- Stateful persistence-context 方法：`@Persist`、`@Merge`、`@Remove`、`@Refresh`、`@Detach` 等。
- 资源访问方法：访问 provider 底层资源。

项目规则：

- 固定查询优先 `@Find`，必要时 HQL/JPQL/JCQL `@Query`。
- 固定实体写入不要默认调用基础接口 `save`，也不要默认定义 `@Save`。新增走 `insert` / `@Insert`，更新走 `update` / `@Update`，删除走 `delete` / `@Delete`；只有明确需要 upsert 语义时才使用 `save`，并在 Service 里说明并发、审计和唯一约束影响。
- 方法名派生查询仍然严格禁止，即使官方示例中出现 `findBy...`。
- 不要在同一个 Repository 设计里混用 stateless lifecycle annotations 和 stateful persistence-context annotations。需要 stateful 方法时先确认是否真的需要持久化上下文语义。
- 资源访问方法只能作为基础设施或排障例外；业务模块 Service 不暴露 `EntityManager`、Hibernate `Session`、`StatelessSession`、`Query` 等底层对象。

## 分页与排序

- 普通列表分页用 `PageRequest`，Repository 可返回 `Page<T>`。
- 需要调用方传入排序时，在方法参数中加入 `Order<T>` 或 `Sort<?>`。
- 静态排序可用 `@OrderBy`；动态排序优先用生成的 Jakarta Data 静态元模型，例如 `_ArchiveCategory.categoryCode.asc()`，减少字符串字段名。
- 分页和游标查询必须全序排序。Hibernate 文档示例明确提示要把实体 id 放入排序，避免同值记录跨页漂移。
- 如果按业务字段排序，最后追加 `id` 或另一个唯一不可变键作为 tie-breaker。
- `CursoredPage<T>` 适合需要稳定向前/向后翻页的 API；只有排序稳定且字段适合 cursor 编码时才用。
- Service 层对外返回项目自己的分页/cursor DTO，不把 Repository 的游标对象直接变成 HTTP 合同。

示例：

```java
@Transactional(readOnly = true)
@Find
@OrderBy("updatedAt")
@OrderBy("id")
Page<ArchiveCategory> page(
        Restriction<ArchiveCategory> restriction,
        PageRequest pageRequest);
```

如果需要动态排序：

```java
@Transactional(readOnly = true)
@Find
Page<ArchiveCategory> page(
        Restriction<ArchiveCategory> restriction,
        PageRequest pageRequest,
        Order<ArchiveCategory> order);
```

调用方必须确保 `order` 最终包含唯一 tie-breaker，例如 `_ArchiveCategory.id.asc()`。

## Count、Exists 与唯一性检查

- `existsById` 这类基础接口方法可用于按主键存在性判断。
- 业务唯一性检查不要写 `existsByCode...` 方法名派生查询。用 `@Find + Limit.of(1)`、稳定投影、或固定 HQL `count` 查询表达。
- 有逻辑删除时，唯一性检查必须和部分唯一索引口径一致，只看 `deleted_flag = false` 的未删除记录。
- 写入前 exists/count 只能优化错误提示，不能替代数据库唯一索引；并发最终以数据库约束为准。

## Delete、Bulk Update 与逻辑删除

- Controller 不直接调用 Repository delete；删除入口走 Service，Service 负责权限、业务状态、审计、乐观锁、逻辑删除和错误映射。
- 配置了 Hibernate `@SoftDelete` 的固定实体，通过 Hibernate/Jakarta Data 实体删除路径会转为更新删除标记；MyBatis 或原生 SQL 路径不会自动生效。
- `@Delete` 或 HQL bulk delete/update 只适合明确的固定实体批处理。涉及 `@Version`、审计字段、生命周期事件或软删除语义时，优先逐实体 Service 流程，或在 HQL/MyBatis 中显式补齐这些条件。
- HQL bulk update 默认不更新版本；需要版本语义时看 `references/hibernate-locking-updates.md`。

## Validation、事务与异常

- Repository 方法可以配合 Bean Validation 注解表达参数约束，但纯校验不要成为事务边界的理由。
- 事务边界优先放在 Service；Repository 上只保留必要的只读或固定写入语义，避免 Controller 绕过业务规则直接调用 Repository。
- `insert(entity)`、`update(entity)`、`delete(entity)` 这类实体生命周期操作可能触发乐观锁或不存在异常。Service 负责转成 AIP 风格错误响应，例如并发冲突用 `ABORTED` 或符合项目约定的冲突状态。

## Stream 与资源生命周期

- Repository 不对外返回 `Stream`、游标或依赖会话生命周期的对象。
- 如果官方基础接口提供 stream/list 等多种返回形态，业务 Service 必须在 `@Transactional` 方法内消费完依赖 session 的结果，再转换为稳定集合、分页 DTO 或业务结果。
- 大结果集导出、报表和批处理若需要可控 fetch size、游标、批量写入或 PostgreSQL 执行计划，优先留在 MyBatis。
