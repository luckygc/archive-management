# Hibernate 逻辑删除参考

## Hibernate @SoftDelete

Hibernate 提供一等逻辑删除支持：`@SoftDelete`。

```java
@Entity
@SoftDelete(columnName = "deleted_flag")
class ArchiveFieldLayout {
    @Id
    private Long id;
}
```

文档要点：

- `@SoftDelete` 可用于实体和集合。
- indicator column 存储逻辑删除状态。
- 可通过 `columnName` 指定列名。
- `SoftDeleteType.DELETED` 表示“true = 已删除”；默认列名通常是 `deleted`。
- `SoftDeleteType.ACTIVE` 表示“true = 有效”；默认列名通常是 `active`。
- `TIMESTAMP` strategy 可用删除时间表达删除状态。
- truth-based strategy 支持 converter，例如 true/false、yes/no、numeric boolean 或自定义 `AttributeConverter<Boolean, ?>`。

## 项目规则

- 固定实体表如果采用逻辑删除，优先评估 Hibernate `@SoftDelete(columnName = "deleted_flag")`。
- 当前项目已有 `deleted_flag boolean not null default false` 语义，优先使用 DELETED 语义：`false = 未删除`，`true = 已删除`。
- 逻辑删除不是回收站。用户明确要求回收站、恢复、保留删除原因、删除批次或独立列表时，应单独设计业务回收站表/流程。
- 带逻辑删除的唯一性仍然按仓库规则使用部分唯一索引：`where deleted_flag = false`，只约束未删除记录。
- MyBatis 查询不会自动获得 Hibernate `@SoftDelete` 过滤；动态 SQL 必须显式包含 `deleted_flag = false`。
- MyBatis 删除路径也不会自动执行 Hibernate soft delete；必须显式 `update ... set deleted_flag = true`，并维护 version 或状态条件。
- Repository 删除路径是否使用 Hibernate `@SoftDelete`，必须通过真实实现验证后再大规模使用。
- 当前 `SecurityAuditingInterceptor` 只验证了明确新增和修改回调；不要假设 `@SoftDelete` 的 Repository 删除会触发修改回调或自动维护通用更新时间与更新人。
- 若删除必须维护通用更新时间与更新人，先用集成测试证明具体 Repository 删除路径；否则设计显式业务删除更新或 MyBatis SQL，并继续从 `AuditContextProvider` 取值。每组通用字段只能有一个来源。
- `deletedBy`、`deletedAt`、删除原因等是业务删除字段，由 Service 按业务合同显式维护，不与通用审计字段合并。

## 何时不用 @SoftDelete

不用或谨慎使用：

- 动态档案表。
- 有独立回收站业务语义的删除。
- 删除动作需要写复杂业务审计、级联业务校验、异步清理或多表状态联动。
- 同一表主要由 MyBatis 动态 SQL 维护，Repository 只读少写；此时更容易出现过滤/删除语义不一致。

## 删除流程建议

固定实体逻辑删除应走服务方法，不让 Controller 直接调用 Repository delete：

1. 读取实体。
2. 校验 version、业务状态和是否已删除。
3. 选择已经集成验证的 Repository 删除路径，或显式业务删除更新/MyBatis SQL。
4. 按业务合同显式维护 `deletedBy`、`deletedAt`、删除原因等业务字段。
5. 写业务审计表，前提是删除有业务动作语义。
6. 捕获乐观锁异常，转换成冲突响应；不要由 Service 预填通用审计字段。
