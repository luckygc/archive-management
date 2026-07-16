# Jakarta Data Repository 功能面参考

## 按需声明方法

直接声明带 `@Repository` 的窄接口，从目标 Service 的真实调用反推方法集合。每个源码方法都要有显式 Repository 操作注解；不要从通用接口起步，也不要提前暴露整套 CRUD。

可按需选择：

- 固定字段读取使用 `@Find`，复杂固定实体表达式使用 `@Query` 或 Hibernate `@HQL`。
- 新增、修改、删除分别声明 `@Insert`、`@Update`、`@Delete` 方法。
- `@By` 只解决参数名无法直接匹配属性、嵌套路径或歧义。
- 固定字段动态条件使用 `Restriction<T>`，排序与限制使用 `Order<T>`、`Sort<?>`、`Limit`。
- 当前业务没有调用的方法不声明；方法名不承担查询派生职责。

## 分页与排序

- 普通列表按需接收 `PageRequest` 并返回 `Page<T>`；确有稳定前后翻页需求时才使用 `CursoredPage<T>`。
- 每个分页或游标查询都必须形成稳定全序，在业务排序后追加实体 ID 或其他唯一、不可变键。
- 动态排序优先使用生成的 Jakarta Data 元模型；只有框架没有类型安全入口时才使用字符串属性名。
- Service 把分页结果转换为项目自己的业务或 HTTP 类型，不让 provider 游标结构成为外部合同。

```java
@Find
@OrderBy("updatedAt")
@OrderBy("id")
Page<ArchiveCategory> page(
        Restriction<ArchiveCategory> restriction,
        PageRequest pageRequest);
```

## Count、Exists 与唯一性

- 只有真实用例需要时才声明 count 或 exists 查询，并显式使用 `@Find`、`@Query` 或 `@HQL`。
- 主键存在性可用 `@Find` 的 `Optional<Entity>` 或轻量投影表达；业务唯一性可用 `@Find` 配合 `Limit.of(1)`，或固定实体 count 查询表达。
- 有逻辑删除时，让查询条件与部分唯一索引保持一致，只检查未删除记录。
- 写入前检查只改善错误提示，不能替代数据库唯一约束；并发结果以 PostgreSQL 约束为准。

## 删除与批量更新

- Controller 不直接调用 Repository；Service 负责权限、状态、审计、乐观锁、逻辑删除和错误映射。
- 只在用例明确时声明 `@Delete`、HQL bulk delete 或 bulk update。
- 批量语句涉及 `@Version`、通用审计、业务审计或逻辑删除时，在语句与 Service 流程中显式补齐这些语义；HQL bulk update 默认不会推进版本。
- 动态表、报表、大结果集和 PostgreSQL 特化批处理继续使用 MyBatis。

## 事务与资源生命周期

- 事务边界优先放在 Service；纯参数校验不因为 Repository 调用而开启事务。
- Repository 不返回 `Stream`、Hibernate `Session`、`Query`、游标或 provider 资源。
- 若底层能力确需流式读取，在 `@Transactional` 方法内消费完成并转换为稳定集合或业务结果，不把资源生命周期交给 Controller。
- 大结果集导出若需要可控 fetch size、游标或执行计划，使用 MyBatis 并在 Service 内关闭资源。
