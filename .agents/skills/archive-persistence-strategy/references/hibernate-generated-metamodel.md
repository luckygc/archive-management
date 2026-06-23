# Hibernate 生成实体辅助类参考

来源：Context7 `/hibernate/hibernate-orm` 官方文档，以及本仓库 `server/target/generated-sources/annotations` 当前编译产物。

## 生成物类型

项目 `server/pom.xml` 已配置 `hibernate-processor`，编译后会在 `server/target/generated-sources/annotations` 生成多类辅助代码：

- `Entity_`：Jakarta Persistence / JPA 静态元模型，例如 `ArchiveCategory_`。
- `_Entity`：Jakarta Data 静态元模型，例如 `_ArchiveCategory`。
- `_Repository`：Hibernate 生成的 Jakarta Data Repository 实现，例如 `_ArchiveCategoryDataRepository`。
- `Repository_`：Repository 相关静态元模型，例如 `ArchiveCategoryDataRepository_`。
- `entity/index/*`：Hibernate processor 生成的实体索引辅助文件。

这些都是编译产物，不要手工编辑，也不要提交 `target/`。

## 何时用哪一个

优先规则：

- 在 Jakarta Data Repository 的 `Restriction<T>`、`Restrict`、`Order<T>`、`Sort<?>`、分页排序中，优先使用 `_Entity` 形式的 Jakarta Data metamodel。
- 在 Jakarta Persistence Criteria、Hibernate `SelectionSpecification`、实体图或其他 JPA metamodel API 中，使用 `Entity_` 形式的 Jakarta Persistence metamodel。
- Repository 实现类 `_EntityDataRepository` 是 provider 生成代码，只用于理解运行时行为和排障，不作为业务代码依赖对象。

本仓库实际示例：

```java
// Jakarta Data metamodel
_ArchiveCategory.categoryCode
_ArchiveCategory.deletedFlag
_ArchiveCategory.id.asc()

// Jakarta Persistence metamodel
ArchiveCategory_.categoryCode
ArchiveCategory_.deletedFlag
```

## 动态条件与排序

固定实体字段上的动态条件不要写字符串字段名。优先写：

```java
Restriction<ArchiveCategory> restriction =
        Restrict.all(
                _ArchiveCategory.categoryCode.equalTo(categoryCode),
                _ArchiveCategory.deletedFlag.equalTo(false));

Order<ArchiveCategory> order =
        Order.by(_ArchiveCategory.sortOrder.asc(), _ArchiveCategory.id.asc());
```

这样改实体字段名时，编译期能暴露调用方问题。不要写：

```java
Order.by(Sort.asc("categoryCode"));
```

除非框架 API 没有类型安全入口，或者字段名来自经过白名单校验的外部配置。

## 与 `@Find` 参数名检查的关系

- `@Find` 方法参数名直接匹配实体属性时，Jakarta Data/Hibernate processor 会做编译期检查。
- 这种情况下不需要为了“显式绑定”而写 `@By`。
- `@By` 只用于参数名和实体属性名不一致、嵌套路径、或需要消除歧义的场景。
- 生成 metamodel 主要解决程序化动态条件、动态排序、Criteria/SelectionSpecification 的字段引用问题，不替代 `@Find` 的参数名匹配规则。

## 编译验证

新增或修改实体、Repository、`@Find`、投影、`Restriction<T>`、`Order<T>` 后，至少运行后端编译，让 `hibernate-processor` 重新生成并校验：

```bash
mvn -pl server compile
```

需要核对生成物时查看：

```bash
server/target/generated-sources/annotations
```

只把生成物作为诊断输入，不把它们变成源码依赖之外的手写合同。

## 边界

- 生成 metamodel 只覆盖固定实体字段；档案动态字段、动态表名、动态列名仍然归 MyBatis 和显式 SQL 标识符校验。
- 不要用 `_Repository` 生成实现绕过 Repository 接口或 Service。
- 不要把 `target/generated-sources` 里的类名硬编码到文档外的业务约定；以实体和 Repository 源码为真相，生成类是编译期辅助。
- 如果生成类不存在，先检查实体是否被 annotation processor 识别、Maven 编译是否成功、`hibernate-processor` 是否仍在 annotation processor path 中。
