---
name: archive-persistence-strategy
description: Use when changing archive-management persistence choices or boundaries involving entities, Repository methods, Mapper interfaces or XML, dynamic tables, complex SQL, PostgreSQL batch work, or auditing.
---

# 档案持久化策略

## 决策流程

1. 先读最近的 `AGENTS.md`、`docs/architecture.md`、对应业务 OpenSpec 与活动 change，再检查目标子域已有实体、Repository、Mapper 和 XML 模式。发生冲突时先校准这些真相源。
2. 按数据与查询责任选择入口：固定稳定表、普通实体生命周期和固定字段查询使用 Jakarta Data；动态表或列、复杂搜索、报表、DDL、PostgreSQL 批处理及需要显式执行计划的 SQL 使用 MyBatis。混合入口按真实责任拆分，不为统一框架移动清晰 SQL。
3. 固定 Repository 直接标注 `jakarta.data.repository.Repository`，不继承通用接口；只声明当前 Service 调用的方法，并使用具体实体签名。每个方法显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 `@HQL`，不使用方法名派生或可同时表示新增和修改的模糊写入语义。
4. 让 Service 显式判断 create、update、delete 分支并承载事务、权限、状态与错误映射。不要向业务边界外泄 Hibernate `Session`、`Query`、`Stream`、游标或其他依赖会话生命周期的对象。
5. 统一从 `AuditContextProvider` 取得时间与用户。固定实体由 Hibernate 无状态会话审计拦截器维护通用字段；MyBatis 插件向参数 Map 注入 `_audit`，XML 显式引用。允许未认证用户为 `null`；业务动作或责任人字段由业务用例显式维护。不要使用实体自动时间注解，也不要由 Service 预填通用审计字段。
6. 运行最窄的相关编译或测试；Repository、包边界和审计改动必须覆盖对应 ArchUnit 测试，Java 改动执行 Spotless。

## Reference 路由

- 新增或调整 Jakarta Data 实体、Repository、固定字段查询、`Restriction` 或投影时，读 `references/jakarta-data.md`。
- 判断按需方法、分页排序、count/exists、删除或资源生命周期时，读 `references/jakarta-data-repository-features.md`。
- 使用生成元模型时，读 `references/hibernate-generated-metamodel.md`；编写固定实体 HQL、投影或 CTE 时，读 `references/hibernate-hql.md`。
- 调整通用审计、混合写入或 Envers 时，读 `references/hibernate-auditing.md`。
- 调整乐观锁或局部更新时，读 `references/hibernate-locking-updates.md`；调整逻辑删除时，读 `references/hibernate-soft-delete.md`；评估批量新增时，读 `references/hibernate-batch-inserts.md`。
