# Hibernate 批量插入参考

来源：Context7 `/hibernate/hibernate-orm` 官方文档，查询主题为 JDBC batching、`hibernate.jdbc.batch_size`、`hibernate.order_inserts`、Hibernate Data Repositories `@Insert` 和 `StatelessSession.insert()`。

## JDBC batching

- `hibernate.jdbc.batch_size` 控制 Hibernate 在要求 JDBC driver 执行 batch 前最多合并多少条 statement；`0` 或负数表示禁用。
- `hibernate.order_inserts` 会强制排序 insert 以提高可 batch 的机会，但官方说明有性能成本，必须在启用前后 benchmark。
- Hibernate 可以按 Session 覆盖 JDBC batch size，但本项目业务模块不要直接暴露 Hibernate `Session`。

## ID 生成限制

- 使用 `GenerationType.IDENTITY` 的实体不能假定 Hibernate insert 能有效 JDBC batching。
- 如果固定实体确实需要 Hibernate 批量插入能力，先评估改为 sequence + pooled optimizer，或应用侧生成 ID。
- 是否调整 ID 生成策略是 DDL、实体、迁移和兼容性变更，不能作为局部性能优化顺手修改。

## Jakarta Data / Hibernate Repository

项目固定实体仓库默认继承 `CrudRepository<Entity, Id>`；需要批量插入时，在该仓库上补充显式生命周期方法。

Hibernate Data Repositories 文档给出的批量插入形式：

```java
@Insert
void add(Book... books);
```

官方说明该生命周期方法映射到 `StatelessSession.insert()`。项目使用时：

- 只用于固定实体表和稳定列。
- 放在 Repository 或 Service 内部，由 Service 维护事务、校验、审计字段和错误映射。
- 不把 `StatelessSession`、Hibernate `Session` 或依赖 session 生命周期的对象暴露为业务模块合同。

## 选择边界

- 少量固定实体批量新增：优先 Jakarta Data Repository `@Insert`，保持 Service 事务边界；不要用 `save` 做默认批量新增入口，除非业务明确要求 upsert。
- 大批量导入、动态表、动态列、报表、全文检索投影、PostgreSQL 特化 SQL：继续 MyBatis。
- PostgreSQL 批量路径可以按场景评估 multi-values、`unnest`、临时表或 `COPY`，不要为了“统一 Repository”牺牲 SQL 可控性。
