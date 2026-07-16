# Jakarta Data Repository 参考

## 直接声明窄接口

为每个固定实体直接声明 `@Repository` 接口，只加入当前 Service 已有用例调用的方法。不要继承通用 Repository，也不要为了可能出现的用例提前暴露列表、计数、删除或批量能力。

每个方法必须使用 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL` 等显式操作注解。方法名可以表达业务意图，但不能作为查询生成合同。参数和返回值使用具体实体类型；Jakarta Data provider 要求的可空返回与非空参数分别使用 `jakarta.annotation.Nullable`、`jakarta.annotation.Nonnull`。

```java
import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

@Repository
public interface ArchiveCategoryDataRepository {

    @Find
    Optional<ArchiveCategory> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveCategory insert(@Nonnull ArchiveCategory entity);

    @Update
    ArchiveCategory update(@Nonnull ArchiveCategory entity);

    @Delete
    void delete(@Nonnull ArchiveCategory entity);
}
```

让 Service 在写入前明确选择新增、修改或删除分支，完成权限、状态、校验和异常映射；Repository 不提供可同时代表多个生命周期的写入入口。固定实体写入继续通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不依赖一级缓存或脏检查。

## 固定字段查询

- 参数名与实体属性一致时直接使用 `@Find`；只有名称无法一致、嵌套路径或需要消除歧义时才加 `@By`。
- 固定实体表达式使用 `@Query` 或 `@HQL`，保持实体查询语义；动态标识符、原生 PostgreSQL SQL、DDL 和动态列结果交给 MyBatis。
- 只为真实用例声明 `Restriction<T>`、`Order<T>`、`Sort<?>`、`Limit`、分页或投影方法。
- 优先使用 `hibernate-processor` 生成的 `_Entity` 属性构造动态条件和排序，避免字符串字段名。

```java
@Find
List<ArchiveFieldLayout> search(
        Restriction<ArchiveFieldLayout> restriction,
        Order<ArchiveFieldLayout> order,
        Limit limit);
```

`Restriction<T>` 只组合固定实体字段：

```java
Restriction<ArchiveFieldLayout> restriction =
        Restrict.all(
                _ArchiveFieldLayout.categoryId.equalTo(categoryId),
                _ArchiveFieldLayout.surface.equalTo(ArchiveLayoutSurface.table),
                _ArchiveFieldLayout.visible.equalTo(true));

List<ArchiveFieldLayout> rows =
        repository.search(
                restriction,
                Order.by(
                        _ArchiveFieldLayout.rowOrder.asc(),
                        _ArchiveFieldLayout.id.asc()),
                Limit.of(200));
```

可按真实条件使用 `Restrict.all(...)`、`Restrict.any(...)`、`Restrict.not(...)` 和 `Restrict.unrestricted()`。字段编码来自分类配置、需要拼接表列名或返回动态 `Map` 时，不要使用该能力。

## 投影与分页

稳定、固定的少量字段可由 `@Find` 返回 record 或接口式投影：

```java
record ArchiveLayoutSummary(Long fieldId, boolean visible, int rowOrder, int colOrder) {}

@Find
List<ArchiveLayoutSummary> summarize(
        Long categoryId,
        ArchiveLayoutSurface surface,
        Order<ArchiveFieldLayout> order);
```

分页和游标查询必须提供稳定全序，最后追加实体 ID 或其他唯一且不可变的键。Service 将 Repository 的实体、投影或分页结果转换为业务结果和 HTTP 响应，不把 provider 类型直接作为外部合同。

固定实体标注 `@SoftDelete` 时，Hibernate Repository 查询自动施加软删除条件，不在实体签名、`Restriction` 或生成元模型中重复声明软删除属性。MyBatis SQL 仍需显式过滤物理列 `deleted_flag = false`。

## 更新与资源边界

- 局部修改先读取当前实体，校验状态与版本，只应用请求中出现的字段，再调用显式 `@Update` 方法；不要提交稀疏实体。
- 需要并发保护的固定可编辑实体使用整数 `@Version`，并由 Service 转换乐观锁异常。
- Repository 不返回 `Stream`、底层资源或依赖会话生命周期的对象；如第三方能力确需流式消费，在事务内完成消费并转换为稳定结果。
- MyBatis 写入不会触发 Hibernate 实体路径；通用审计按 `hibernate-auditing.md` 处理。
