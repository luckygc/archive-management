# Hibernate HQL 参考

## 实体映射

- 固定项目表使用 Jakarta Persistence 注解建模：`@Entity`、`@Table`、`@Id`、`@Column`。
- 主键自增字段按项目现有风格使用 `@GeneratedValue(strategy = GenerationType.IDENTITY)`。
- 枚举字段默认用 `@Enumerated(EnumType.STRING)`；enum 常量名使用对外和入库一致的大写可读字符串，多个英文单词用下划线分隔。
- 通用创建/更新时间由 `AuditContextProvider` 和 Hibernate 无状态会话审计拦截器统一维护，不在实体映射中另设生成来源。

## HQL 不是 SQL

Jakarta Data Repository 中需要固定实体表达式时，直接在窄接口方法上显式标注 `@HQL`，不写数据库原生 SQL。

允许：

```java
@HQL("""
       where categoryId = ?1
         and surface = ?2
       """)
List<ArchiveFieldLayout> listActive(Long categoryId, ArchiveLayoutSurface surface);
```

实体标注 `@SoftDelete` 后，Hibernate 会为固定实体查询自动施加软删除条件；HQL 不增加虚构的 Java 属性。MyBatis SQL 不经过该机制，仍须显式过滤物理列 `deleted_flag = false`。

避免：

```java
@HQL("select * from am_archive_field_layout where category_id = ?1")
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

- 业务模块只通过直接、窄的 Jakarta Data Repository 使用 HQL，不直接创建或暴露 Hibernate `Session`、`Query`、游标、`Stream` 等会话资源。
- 如确需 Hibernate 底层能力，封装在 `infrastructure`，不要把它变成 archive 模块公开合同。
