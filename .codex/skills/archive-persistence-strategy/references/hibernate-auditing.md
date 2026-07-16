# Hibernate 与 MyBatis 审计参考

## 唯一上下文

使用基础设施层 `AuditContextProvider` 作为所有持久化写入的当前时间和用户来源。一次 `current()` 调用返回同一份 `AuditContext`：

- `now` 始终存在，由注入的 `Clock` 生成。
- `userId` 在未认证、匿名或无法识别主体时允许为 `null`。

Hibernate 与 MyBatis 都依赖该 provider，不直接读取各自的时钟或安全上下文。不要在实体上增加自动生成通用审计时间的注解，也不要让 Service 预填 `createdAt`、`updatedAt`、`createdBy`、`updatedBy`。

## 固定实体写入

固定 Repository 通过 Hibernate `StatelessSession` / `EntityAgent` 执行。`SecurityAuditingInterceptor` 注入 `AuditContextProvider`，并按明确生命周期维护通用字段：

- `onInsert` 同时填充实体支持的创建与更新时间；创建人和更新人可为 `null`。
- `onUpdate` 只覆盖实体支持的更新时间与更新人。
- 实体通过 `CreationTimeAuditable`、`CreationAuditable`、`UpdateTimeAuditable`、`UpdateAuditable` 表达自己具有哪些通用字段。

Service 只选择明确的新增或修改 Repository 方法。不要再引入第二套实体 listener、生命周期事件或本地时间来源来填同一组字段。

## MyBatis 写入

`MyBatisAuditingInterceptor` 对 Map 参数调用同一个 `AuditContextProvider`，并写入保留键 `_audit`。Mapper XML 必须显式引用所需值，插件不改写 SQL：

```xml
insert into am_example (created_by, created_at, updated_by, updated_at)
values (#{_audit.userId}, #{_audit.now}, #{_audit.userId}, #{_audit.now})
```

```xml
update am_example
set updated_by = #{_audit.userId},
    updated_at = #{_audit.now}
where id = #{id}
```

Mapper 方法需要多个业务参数时使用 `@Param` 或显式 Map，使插件可以注入 `_audit`。SQL 必须按创建或修改语义分别引用字段；不要由调用方伪造或覆盖该键。

PostgreSQL `INSERT ... RETURNING` 在 MyBatis 中可通过 `selectOne` 执行，并进入 `Executor.query` 而不是 `Executor.update`。插件必须同时覆盖 4 参数和 6 参数 `Executor.query` 签名；两条路径都只修改原参数 Map，保留原 `BoundSql` 与 SQL，不做隐式 SQL 改写。

## 业务字段边界

以下字段表达业务动作、归属或责任，不属于通用持久化审计：

- `deletedBy`、`lockedBy`、`owner`、`requestedBy`、`operatedBy`；
- `operatedAt`、业务生效时间、审批时间；
- 删除原因、操作类型、状态迁移原因。

由业务用例显式维护这些字段，并按对应 OpenSpec 决定是否要求已认证用户。需要操作历史时继续写业务审计表；通用字段不能代替业务流水。

## Envers 边界

只有固定实体明确需要历史快照、revision 查询或属性级变更标记时才评估 Envers。引入前检查依赖、Flyway 表结构、表命名、数据增长、清理策略和查询入口。

不要用 Envers 承载动态档案字段或归档、锁定、移交、借阅等业务动作流水；已有业务审计表时继续使用业务模型。
