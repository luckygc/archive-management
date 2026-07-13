# Jakarta Data Repository 参考

## 仓库接口

- Jakarta Data 仓库接口使用 `@Repository` 标注。
- 固定实体表优先继承 `CrudRepository<Entity, Id>`，复用更完整的固定实体生命周期操作。
- 抽象方法主要分为实体生命周期方法、注解查询方法、自动查询方法和资源访问方法。
- 实体类型通常由泛型父接口确定，例如 `CrudRepository<Product, Long>`。
- 当前项目 `server/pom.xml` 使用 `jakarta.data-api 1.1.0-M3`，并配置 `hibernate-processor`，可利用 Jakarta Data 1.1 的 `Restriction<T>` / `Restrict` 动态条件和生成的静态元模型。

## 查询风格

本项目仓库规则：

1. 优先使用 `@Find`，让参数名直接匹配实体属性名，依赖 Jakarta Data 的编译时检查。
2. 只有参数名和实体属性名不一致、或需要显式字段路径映射时，才使用 `@By`。
3. 严格禁止 Query by Method Name。不要写 `findBy...`、`deleteBy...`、`countBy...` 这类按方法名派生查询的方法。
4. 固定实体写入不要默认使用 `save` / `@Save`；新增、修改、删除分别用语义明确的 `insert` / `@Insert`、`update` / `@Update`、`delete` / `@Delete`，只有明确需要 upsert 时才使用 `save`。
5. 动态分页、排序、限制结果数使用 Jakarta Data 的 `PageRequest`、`Order<T>`、`Sort<?>`、`Limit` 等特殊参数。
6. 固定实体字段上的动态条件优先使用 `Restriction<T>` / `Restrict`，并优先用 `hibernate-processor` 生成的 Jakarta Data 静态元模型属性，例如 `_ArchiveCategory.categoryCode`。
7. 稳定的部分字段返回优先用 Jakarta Data 投影，返回 record 或接口式稳定投影，不照搬 Spring Data/JPA 的构造函数投影写法。
8. 固定实体查询需要手写表达式时，使用 Jakarta Data `@Query` 写实体查询语义，避免数据库原生 SQL。
9. 一旦查询需要动态表名、动态列名、PostgreSQL DDL、执行计划控制、全文索引或批处理 SQL，退出 Jakarta Data，交给 MyBatis。

示例：

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

`@By` 的例外示例：

```java
@Transactional(readOnly = true)
@Find
List<Person> find(@By("address.zipCode") int zipCode);
```

## 动态条件

Jakarta Data 1.1 提供 `Restrict` 和 `Restriction<T>`，可程序化组合固定实体字段上的动态条件。项目里这类动态条件优先走 Repository，不要直接降级到 MyBatis。

```java
Restriction<ArchiveFieldLayout> restriction =
        Restrict.all(
                _ArchiveFieldLayout.categoryId.equalTo(categoryId),
                _ArchiveFieldLayout.surface.equalTo(ArchiveLayoutSurface.table),
                _ArchiveFieldLayout.deletedFlag.equalTo(false));

if (visibleOnly) {
    restriction = Restrict.all(restriction, _ArchiveFieldLayout.visible.equalTo(true));
}

List<ArchiveFieldLayout> rows =
        repository.search(
                restriction,
                Order.by(_ArchiveFieldLayout.rowOrder.asc(), _ArchiveFieldLayout.colOrder.asc()),
                Limit.of(200));
```

可用组合：

- `Restrict.all(...)` 表示 AND。
- `Restrict.any(...)` 表示 OR。
- `Restrict.not(...)` 表示 NOT。
- `Restrict.unrestricted()` 表示无筛选，可用于可选条件起点。

## 投影

Jakarta Data 的 `@Find` 支持部分属性投影，返回 Java record 这类稳定投影类型。Hibernate 实现下不需要按 Spring Data 或 JPA 构造函数投影思路组织查询；投影类型列出需要的字段即可。

```java
record ArchiveLayoutSummary(Long fieldId, boolean visible, int rowOrder, int colOrder) {}

@Transactional(readOnly = true)
@Find
List<ArchiveLayoutSummary> summarize(
        Long categoryId,
        ArchiveLayoutSurface surface,
        boolean deletedFlag,
        Order<ArchiveFieldLayout> order);
```

适用场景：

- 列表页、选择器、配置页只需要固定少量字段。
- 返回字段集合稳定，可用 record 表达。
- 不需要动态列名，也不需要 `Map`。

不适用场景：

- 档案动态字段结果列由分类配置决定。
- 查询输出字段集合不稳定。
- 查询必须返回动态列 `Map`。

这些场景仍然用 MyBatis。

## 动态条件边界

Jakarta Data 的动态能力适合这些情况：

- 固定实体字段上的可选筛选。
- 固定实体字段上的 AND/OR/NOT 动态组合。
- 固定字段上的分页、排序、limit。
- 固定实体查询里的静态排序叠加调用方传入排序。

不适合这些情况：

- 字段编码来自档案分类配置，并要拼成列名。
- 查询要跨动态档案表。
- 查询要组合 PostgreSQL 特有全文检索、动态索引或 DDL。
- 查询输出不是实体或稳定 DTO，而是动态列 `Map`。

这些不适合项默认用 MyBatis。

## 生命周期、更新与并发

Jakarta Data 1.1 文档包含生命周期事件：

- `PreInsertEvent` / `PostInsertEvent`
- `PreUpdateEvent` / `PostUpdateEvent`
- `PreDeleteEvent` / `PostDeleteEvent`
- `PreUpsertEvent` / `PostUpsertEvent`

这些事件可用于固定实体的校验、补充审计字段或触发领域内副作用。项目使用时必须注意：MyBatis 写入路径不会触发这些 Repository 生命周期事件。

`CrudRepository` 的显式更新方法会区分 insert/update；`update(entity)` 可在实体不存在、已被修改或已被删除时抛 `OptimisticLockingFailureException`。因此固定实体更新优先走“读取实体 -> 应用变更 -> Repository/Hibernate 显式 update”的闭环，避免使用 `save` 模糊新增和更新语义。

局部更新规则：

- PATCH 不能构造只带部分字段的稀疏实体直接 update。
- 先读取当前实体，再只修改请求中出现的字段。
- 请求模型要能区分字段未出现和显式置空。
- 更新固定实体时优先带 `@Version`，并把乐观锁异常转换成 API 冲突响应。
