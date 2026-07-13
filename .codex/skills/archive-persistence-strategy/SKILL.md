---
name: archive-persistence-strategy
description: Use when working in archive-management on persistence entry choices, entity/repository design, or refactoring data access between Jakarta Data Repository, Hibernate/Jakarta Persistence entity mapping, and MyBatis. Trigger for fixed CRUD tables, repository/entity additions, Mapper XML review, dynamic archive table SQL, search/batch SQL, and architecture boundary questions about persistence.
---

# Archive Persistence Strategy

## Overview

Use this skill to decide the persistence entry before editing archive-management data access code. The project default is: fixed entity tables use Jakarta Data Repository + Hibernate/Jakarta Persistence entities first; MyBatis is reserved for dynamic SQL, dynamic archive tables, complex search, batch/report SQL, and mapper-heavy integrations.

## First Checks

- Read the nearest `AGENTS.md` before changing code.
- Read `references/jakarta-data.md` before adding or refactoring a Repository.
- Read `references/jakarta-data-repository-features.md` before choosing a repository base interface, lifecycle method, pagination style, cursor paging, count/exists strategy, or stateful/stateless behavior.
- Read `references/hibernate-generated-metamodel.md` before using generated entity helper classes, static metamodel attributes, `Restriction<T>`, `Order<T>`, Criteria, or generated repository implementations.
- Read `references/hibernate-hql.md` before writing `@Query`, HQL, CTE, or entity mapping details.
- Read `references/hibernate-auditing.md` before adding audit fields, entity listeners, `@CreationTimestamp`/`@UpdateTimestamp`, or considering Envers.
- Read `references/hibernate-locking-updates.md` before adding `@Version`, `@DynamicUpdate`, partial update commands, HQL bulk updates, or conflict handling.
- Read `references/hibernate-soft-delete.md` before adding logical delete fields, `@SoftDelete`, delete APIs, restore/recycle-bin behavior, or unique constraints involving deleted rows.
- Read `references/hibernate-batch-inserts.md` before enabling Hibernate JDBC batching, adding Repository batch insert methods, changing ID generation, or comparing Repository batch writes with MyBatis/PostgreSQL batch SQL.
- Inspect existing package patterns in `server/src/main/java/github/luckygc/am/module/**` and current mapper XML before adding a new entry point.
- Do not move business classes to `common`; keep archive business entities and repositories under `module/archive/**`.

## Decision Rules

Prefer Jakarta Data Repository when all are true:

- The table is a fixed project-owned table with stable columns.
- Queries are ordinary CRUD, lookup by fixed entity fields, simple page/list, or fixed HQL-style updates/deletes.
- The model can be represented as a Jakarta Persistence entity.
- Compile-time repository checks are useful and SQL identifier dynamics are not needed.

Use MyBatis when any are true:

- SQL must address dynamic archive tables or dynamic columns.
- Query shape depends on validated identifiers, dynamic archive field predicates, full-text search, or report-style joins.
- Batch operations need explicit SQL such as `unnest`, generated DDL, index creation, or PostgreSQL-specific execution plans.
- Existing mapper XML already owns the dynamic behavior and moving it would reduce clarity.
- Large imports or batch writes need PostgreSQL-specific SQL such as multi-values insert, `unnest`, temporary tables, or `COPY`.

Use Hibernate/Jakarta Persistence details only through entities and repositories in module code. Do not expose Hibernate `Session`, stateful `Query`, or persistence-context-dependent return types as business contracts; infrastructure-level adapters are the exception.

Repository feature rule:

- Prefer `CrudRepository<Entity, Id>` for new fixed-entity repositories; it exposes a fuller fixed-entity lifecycle surface than `BasicRepository` and is the project default unless a narrower repository is explicitly required.
- Do not use repository `save` or `@Save` as the default write path. Prefer explicit `insert` / `update` / `delete` lifecycle methods so Service code decides create/update/delete branches before writing. Use `save` only for documented upsert semantics.
- Use annotated Jakarta Data lifecycle/query methods only when they add a clear fixed-entity operation. Do not mix stateless method annotations such as `@Insert`, `@Update`, `@Delete`, `@Save` with stateful persistence-context annotations such as `@Persist`, `@Merge`, `@Remove`, `@Refresh`, `@Detach` in the same repository design.
- Use `PageRequest` / `Page<T>` for ordinary offset paging and `CursoredPage<T>` only when the API needs cursor paging and the query has a stable total order.
- Every paged or cursor query must have deterministic ordering; append the entity id or another unique immutable key as the final tie-breaker.
- Do not expose repository resource accessors, `Stream`, or provider resources as business-module APIs. Consume session-dependent results inside a transactional service method.
- Use generated Jakarta Data metamodel classes such as `_ArchiveCategory` for `Restriction<T>`, `Order<T>`, and type-safe fixed-field references. Use Jakarta Persistence metamodel classes such as `ArchiveCategory_` for Criteria/Hibernate programmatic query APIs.

Audit rule:

- Fill fixed entity `created_at` / `updated_at` / `created_by` / `updated_by` through the infrastructure stateless-session auditing interceptor. User fields come from application user context, not from Hibernate timestamp annotations.
- Hibernate `@CreationTimestamp` / `@UpdateTimestamp` can stay on entities as auxiliary generation annotations for explicit entity insert/update paths, but do not treat them as the source of truth for `save`/upsert or MyBatis writes.
- Repository writes currently run through Hibernate `StatelessSession` / `EntityAgent`; use stateless-session interceptors such as `onLoad`、`onInsert`、`onUpdate`、`onUpsert`、`onDelete` for repository-wide audit adaptation. `onUpsert` must only maintain update-side audit fields and must not overwrite `created_at` / `created_by`.
- Hibernate also supports lifecycle callbacks including `PostPersist`、`PostUpdate`、`PostRemove`; use them only after confirming they fire on the selected stateful/stateless write path.
- Keep business operation audit in business audit tables.
- Consider Envers only for fixed entity historical revisions, not for dynamic archive fields or business action logs.

Concurrency and partial update rule:

- Fixed user-editable entities should normally include an integer `@Version` column.
- Partial updates must load the current entity, apply only fields present in the command, and let Hibernate/Jakarta Data update the managed entity.
- Do not update fixed entities by constructing a sparse detached entity; missing fields can be overwritten.
- Consider `@DynamicUpdate` for wide/index-heavy fixed entities, but pair the decision with `@Version`.
- HQL bulk updates do not update `@Version` by default; use `update versioned` or avoid bulk HQL when callbacks/audit/concurrency semantics matter.

Batch insert rule:

- Hibernate JDBC batching is not assumed to be active. Configure `hibernate.jdbc.batch_size` before expecting batching, and benchmark `hibernate.order_inserts` before enabling it.
- `GenerationType.IDENTITY` prevents effective Hibernate insert batching for affected entities. Before choosing Repository/Hibernate batch insert, evaluate sequence + pooled optimizer or application-generated IDs.
- For fixed entity batch create, prefer Jakarta Data Repository lifecycle methods such as `@Insert void add(Entity... entities)` inside a Service transaction; do not use repository `save` for batch create unless upsert is explicitly required.
- Hibernate documents Repository `@Insert` varargs as mapping to `StatelessSession.insert()`. Keep that detail inside Repository/Service implementation; do not expose `StatelessSession` or Hibernate `Session` as business contracts.
- Use MyBatis for dynamic archive tables, dynamic columns, import/report SQL, and PostgreSQL-tuned bulk paths instead of forcing them through Repository abstractions.

Logical delete rule:

- Fixed entities with `deleted_flag` should evaluate Hibernate `@SoftDelete(columnName = "deleted_flag")`, but MyBatis paths must still filter and update `deleted_flag` explicitly.
- Logical delete is not a recycle bin; restore/delete-reason/delete-batch semantics need explicit business workflow design.
- Unique indexes on logically deleted tables must stay partial: `where deleted_flag = false`.

## Hard Bans

- Do not rely on Query by Method Name as the repository contract. Every custom repository method must explicitly declare its operation with annotations such as `@Find`, `@Insert`, `@Update`, `@Delete`, `@Save`, `@Query`, or Hibernate `@HQL`, so a missing annotation cannot silently fall back to provider-derived method-name queries.
- Do not write native SQL in Jakarta Data Repository.
- Do not move dynamic archive table/field SQL into Repository just to avoid MyBatis.
- Do not create full unique indexes on logically deleted tables. Uniqueness must exclude deleted rows with a partial unique index such as `where deleted_flag = false`.

## Repository Pattern

For fixed archive tables:

```java
@Data
@Entity
@Table(name = "am_archive_field_layout")
@SoftDelete(columnName = "deleted_flag")
public class ArchiveFieldLayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ArchiveLayoutSurface surface;

    @Version
    private int version;
}
```

```java
@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFieldLayoutDataRepository
        extends CrudRepository<ArchiveFieldLayout, Long> {

    @Transactional(readOnly = true)
    @Find
    List<ArchiveFieldLayout> find(
            Long categoryId,
            ArchiveLayoutSurface surface,
            boolean deletedFlag,
            Order<ArchiveFieldLayout> order);

    @Transactional(readOnly = true)
    @Find
    List<ArchiveFieldLayout> search(
            Restriction<ArchiveFieldLayout> restriction,
            Order<ArchiveFieldLayout> order,
            Limit limit);

    record LayoutSummary(Long fieldId, boolean visible, int rowOrder, int colOrder) {}

    @Transactional(readOnly = true)
    @Find
    List<LayoutSummary> summarize(
            Long categoryId,
            ArchiveLayoutSurface surface,
            boolean deletedFlag,
            Order<ArchiveFieldLayout> order);
}
```

Repository rules:

- Prefer `@Find` with parameter names matching entity attributes for fixed entity-field conditions; Jakarta Data provides compile-time checks for this style.
- Use `@By` only when the Java parameter name cannot or should not match the entity attribute, or when explicit field path mapping is needed for nested/ambiguous fields.
- Strictly prohibit annotation-less Query by Method Name. Method names may remain business-readable, but `findBy...`, `deleteBy...`, `countBy...`, or similar names are not the query contract unless an explicit Jakarta Data/HQL operation annotation is present.
- Use Jakarta Data special parameters such as `Order<T>`, `Sort<?>`, `Limit`, and `PageRequest` for dynamic sorting, limits, and pagination on fixed entity fields.
- For paged lists, return `Page<T>` or a service DTO built from it; for cursor paging, return a service-level cursor model rather than leaking provider-specific internals.
- Use Jakarta Data `Restriction<T>` and `Restrict` for dynamic conditions on fixed entity fields. Prefer generated Jakarta Data metamodel attributes from `hibernate-processor`, such as `_ArchiveCategory.categoryCode`, instead of stringly typed field names.
- Use Jakarta Data/Hibernate projection for stable partial results. Prefer a compact record or interface-style projection that lists the fields; do not copy Spring Data constructor-projection habits.
- Use integer `@Version` for fixed editable entities that need concurrent update protection. Treat timestamp versions as an exception.
- For PATCH/partial update, load the entity and mutate only present fields; do not submit sparse detached entities.
- Use `@Query` only for fixed HQL/JPQL-style entity queries. Do not write native SQL in Jakarta Data Repository.
- In Hibernate HQL, projection can directly select fields into a record result type. Do not write `select new ...` unless an API specifically requires the JPA standard dynamic-instantiation form.
- HQL can express CTEs, including recursive CTEs; do not move a fixed entity query to MyBatis merely because it needs a `with` clause.
- If the query needs dynamic identifiers, dynamic archive fields, PostgreSQL DDL/search specifics, or dynamic `Map` results, keep it in MyBatis.

Dynamic restriction example:

```java
Restriction<ArchiveFieldLayout> visibleTableLayout =
        Restrict.all(
                _ArchiveFieldLayout.categoryId.equalTo(categoryId),
                _ArchiveFieldLayout.surface.equalTo(ArchiveLayoutSurface.table),
                _ArchiveFieldLayout.visible.equalTo(true),
                _ArchiveFieldLayout.deletedFlag.equalTo(false));

List<ArchiveFieldLayout> rows =
        repository.search(
                visibleTableLayout,
                Order.by(_ArchiveFieldLayout.rowOrder.asc(), _ArchiveFieldLayout.colOrder.asc()),
                Limit.of(200));
```

## Refactor Workflow

1. Classify each table/query with the decision rules above.
2. For fixed tables, add or reuse an `@Entity` and a `*DataRepository`.
3. Choose `CrudRepository` by default; document any reason to narrow to `BasicRepository`, add stateful repository methods, expose resource accessors, or use cursor paging.
4. Use generated metamodel classes for fixed-field restrictions and sort orders; verify the generated class name under `server/target/generated-sources/annotations` if unsure.
5. Add `@Version` for editable fixed entities unless there is a documented reason not to.
6. Decide logical delete semantics up front: `@SoftDelete` for fixed entities, explicit `deleted_flag` SQL for MyBatis paths, business recycle bin only when required.
7. Express fixed Repository reads with `@Find` and matching parameter names; use `Restriction<T>` for dynamic conditions; use `@By` only for mismatched or explicit field paths; use HQL `@Query` only when `@Find`/`Restriction<T>` is not enough.
8. Use Repository projections for stable partial results instead of loading full entities or writing DTO constructor SQL.
9. For paged/cursor reads, define a stable order with a unique tie-breaker before wiring `PageRequest` or `CursoredPage`.
10. For partial updates, load the entity, apply present fields, fill audit user fields, and rely on Repository/Hibernate optimistic locking.
11. Move simple CRUD/list/delete logic from Mapper XML into repository-backed service code, and replace ambiguous save/upsert writes with explicit insert/update/delete methods where possible.
12. Leave dynamic archive table SQL, dynamic field predicates, generated DDL, search, imports, PostgreSQL-specific batch, and reporting in MyBatis.
13. Remove dead Mapper methods and XML fragments only after all call sites are moved.
14. Update ArchUnit tests if package boundaries or dependency directions change.
15. Validate with the narrowest backend compile/test command available; run Spotless when Java formatting changed.

## Archive Layout Example

`am_archive_field_layout` is a fixed project table and should use Jakarta Data Repository. Its field relationships are stable, and list/detail/edit layout rows are normal entity lifecycle data.

For this table, use a Repository query over `ArchiveFieldLayout` and `ArchiveField` entity fields where possible. If ordering or filtering cannot be cleanly represented with `@Find`, use fixed HQL `@Query`; do not write `select ... from am_archive_field_layout` SQL in the Repository.

Keep these in MyBatis:

- Dynamic archive record table creation and `ALTER TABLE`.
- Dynamic field search predicates and full-text projection queries.
- Identifier-validated SQL fragments such as archive category table names and column names.
- Batch update/report queries where explicit PostgreSQL SQL is the contract.
