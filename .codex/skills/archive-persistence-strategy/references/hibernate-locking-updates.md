# Hibernate 乐观锁与局部更新参考

来源：Context7 `/hibernate/hibernate-orm`、`/jakartaee/data`，查询时间按本次工作流执行。

## 乐观锁

Hibernate 标准乐观锁入口是 `@Version`：

```java
@Version
private int version;
```

文档也支持 `LocalDateTime` / `Instant` 等时间戳版本：

```java
@Version
private LocalDateTime lastUpdated;
```

项目规则：

- 固定实体表默认要评估是否加 `version` 整数列。
- 数字版本优先于时间戳版本；Hibernate 文档认为时间戳版本相对不如版本号可靠。
- 需要并发保护的配置表、元数据表、用户可编辑表应优先加 `@Version`。
- 动态档案表和复杂批处理仍按 MyBatis 语义单独设计并发条件。

Jakarta Data `CrudRepository.update(entity)` 在未找到实体、被修改或被删除时可抛 `OptimisticLockingFailureException`。因此固定实体更新优先走 Repository 读改写闭环，而不是手写无版本条件的 SQL。

## 局部更新

Hibernate 默认通过 persistence context 的 dirty checking 识别实体属性变更；`@DynamicUpdate` 可让生成的 SQL UPDATE 只包含已修改列。

```java
@Entity
@DynamicUpdate
class ArchiveFieldLayout {
    @Id
    private Long id;

    @Version
    private int version;
}
```

使用规则：

- 局部更新固定实体时，先加载实体，再按 PATCH 命令修改明确出现的字段，最后由 Hibernate/Jakarta Data 提交。
- 不要用“只填了部分字段的新实体”直接 `update/save`，这会把未出现字段覆盖成 `null` 或默认值；其中 `save` 还会模糊新增和更新语义，默认不作为固定实体写入入口。
- 如果 API 允许显式置空，PATCH command 必须能区分“字段未出现”和“字段出现且为 null”；不能只靠 Java `null` 表达两种语义。
- 对列多、索引多、更新字段少的固定实体，可考虑 `@DynamicUpdate`。
- `@DynamicUpdate` 应与 `@Version` 一起评估；文档提示缺少 version 时，动态更新可能带来并发下列值不一致风险。

## PATCH 请求建模

局部更新必须明确请求语义。推荐把请求建模为“字段是否出现”可判定的结构，例如：

- 对简单命令，用 `JsonNullable<T>`、自定义 `PatchField<T>` 或等价结构表达三态：未出现、出现为 null、出现为值。
- 对不允许显式置空的字段，可以继续用普通 nullable 字段，但必须在文档/校验里说明 `null` 表示未更新。
- 对批量字段补丁，可用 `Map<String, PatchField<?>>` 或明确的 `Set<String> updateMask` 辅助判断。

示例流程：

```java
ArchiveFieldLayout entity = repository.findById(id).orElseThrow(...);

if (request.version() != entity.getVersion()) {
    throw conflict("记录已被其他用户修改");
}
if (request.visible().isPresent()) {
    entity.setVisible(request.visible().orElse(null));
}
if (request.rowOrder().isPresent()) {
    entity.setRowOrder(request.rowOrder().orElseThrow());
}
entity.setUpdatedBy(currentUserId);
```

反模式：

```java
ArchiveFieldLayout entity = new ArchiveFieldLayout();
entity.setId(id);
entity.setVisible(request.visible());
repository.update(entity);
```

这个写法会丢失未出现在请求里的字段，也绕过“先读当前状态再做业务校验”的闭环。

## HQL 批量更新

Hibernate HQL 批量 update 默认不更新 `@Version` 字段。需要推进版本时使用 `update versioned`：

```hql
update versioned ArchiveFieldLayout
set visible = :visible
where categoryId = :categoryId
```

边界：

- HQL bulk update 适合固定实体的批量状态修正。
- 如果更新需要实体生命周期回调、逐实体审计字段、业务校验或复杂并发语义，优先用加载实体后逐条更新，或明确设计服务层批处理。
- MyBatis 批量 SQL 必须显式带上版本条件、状态条件或其他业务并发条件；不会自动获得 Hibernate 的 `@Version` 保护。

## 局部更新流程

1. 读取实体，并且只读取未删除记录；若不存在返回 ProblemDetail `code=NOT_FOUND` 或项目既有错误模型。
2. 校验业务状态、删除状态、权限和调用方传入的 version；如果版本不匹配，返回冲突错误。
3. 只应用请求中出现的字段；未出现字段保持原值。
4. 对每个出现字段执行字段级校验；如果字段显式置空，必须确认业务允许置空。
5. 填充 `updated_by` / `updated_at`；若用 Hibernate 时间戳，至少显式填充 `updated_by`。
6. 调用 Repository `update`；只有明确需要 upsert 语义时才使用 `save`，且必须说明并发和审计影响。
7. 捕获 optimistic locking 异常并转换成 API 冲突响应。
