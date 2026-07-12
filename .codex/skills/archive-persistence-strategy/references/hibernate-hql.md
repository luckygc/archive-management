# Hibernate HQL 参考

来源：Context7 `/hibernate/hibernate-orm`，查询时间按本次工作流执行。

## 实体映射

- 固定项目表使用 Jakarta Persistence 注解建模：`@Entity`、`@Table`、`@Id`、`@Column`。
- 主键自增字段按项目现有风格使用 `@GeneratedValue(strategy = GenerationType.IDENTITY)`。
- 枚举字段默认用 `@Enumerated(EnumType.STRING)`，和仓库规则中的小写 snake_case enum 常量持久化保持一致。
- `@CreationTimestamp`、`@UpdateTimestamp` 可以继续沿用项目现有实体风格，但不要因此把审计字段语义挪到 `common`。

## HQL 不是 SQL

Jakarta Data Repository 中如果需要 `@Query`，优先写 HQL/JPQL 风格的实体查询，不写数据库原生 SQL。

允许：

```java
@Query("""
       where categoryId = ?1
         and surface = ?2
         and deletedFlag = false
       """)
List<ArchiveFieldLayout> listActive(Long categoryId, ArchiveLayoutSurface surface);
```

避免：

```java
@Query("select * from am_archive_field_layout where category_id = ?1")
List<ArchiveFieldLayout> listActive(Long categoryId);
```

上面这种表名/列名 SQL 不属于 Repository 默认风格；确实需要 SQL 时改用 MyBatis。

## 投影

Hibernate 的 HQL 投影可以直接 select 字段并映射到 Java record 结果类型，不必写 JPA 标准的 `select new ...` 构造表达式。

推荐形态：

```java
record IsbnTitle(String isbn, String title) {}

@HQL("select isbn, title from Book")
List<IsbnTitle> listIsbnAndTitleForEachBook(Page page);
```

或在普通 HQL 查询里指定结果类型：

```java
record IsbnTitle(String isbn, String title) {}

List<IsbnTitle> rows =
        entityManager.createQuery("select isbn, title from Book", IsbnTitle.class)
                .getResultList();
```

避免把 Spring Data/JPA 构造函数投影习惯带进来：

```java
select new com.example.IsbnTitle(isbn, title) from Book
```

只有当某个 API 明确要求 JPA 标准 dynamic instantiation 时，才使用 `select new`。

## HQL CTE

Hibernate HQL 支持 `with` common table expressions，并支持递归 CTE。它适合固定实体模型上的层级查询或把固定子查询命名后复用。

示例形态：

```hql
with Tree as (
    select root.id as id, root.text as text, 0 as level
    from Node root
    where root.parent is null
    union all
    select child.id as id, child.text as text, level + 1 as level
    from Tree parent
    join Node child on child.parent.id = parent.id
)
select text, level
from Tree
```

边界：

- HQL CTE 能覆盖固定实体层级查询，不要因为出现 CTE 就直接下沉到 MyBatis。
- 若 CTE 内部需要动态表名、动态列名、PostgreSQL 专用索引语义或返回动态列 `Map`，仍然用 MyBatis。

## Hibernate API 边界

- 业务模块不要直接暴露 Hibernate `Session`、有状态 `Query`、游标、`Stream` 等依赖 persistence context 生命周期的对象。
- 如确需 Hibernate 底层能力，封装在 `infrastructure`，不要把它变成 archive 模块公开合同。
