# Repository 与审计统一实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 删除项目自定义 Repository 基类，让每个 Jakarta Data Repository 只声明实际需要的方法；同时让 Hibernate 与 MyBatis 写入共享同一个审计上下文，由各自拦截器统一提供通用审计字段。

**架构：** `AuditContextProvider` 是审计时间和当前用户的唯一来源。Hibernate 拦截器通过审计接口覆盖固定实体的通用审计属性；MyBatis 插件只向参数对象注入保留键 `_audit`，Mapper XML 显式引用该值，不解析或改写 SQL。业务操作人字段（如 `deleted_by`、`locked_by`、规则执行人和对象所有者）仍由业务代码显式传入。

**技术栈：** Java 25、Spring Boot、Hibernate ORM/Jakarta Data、MyBatis、JSpecify、JUnit 5、AssertJ、Mockito、ArchUnit、Maven、Spotless。

## 全局约束

- 不新增 Repository 基类、通用 CRUD 接口、Repository adapter 或方法名派生查询。
- 每个 Repository 直接标注 `jakarta.data.repository.Repository`，不继承任何项目或 Jakarta Data 泛型 Repository。
- Repository 声明的每个方法必须显式使用 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL`。
- 不恢复 `save`、`@Save` 或 upsert 语义。
- 通用审计值由拦截器无条件覆盖调用方传值；一次写入只获取一次 `AuditContext`。
- `AuditContext.now` 非空；未认证、启动期和后台任务的 `userId` 为 `null`。
- Hibernate 与 MyBatis 共用 `AuditContextProvider`，不得分别读取系统时钟或 Spring Security 上下文。
- MyBatis 插件不得修改 `BoundSql.sql`；只覆盖参数映射中的 `_audit`。
- 不把 `deleted_by`、`locked_by`、业务请求发起人、对象所有者等业务字段误归为通用审计字段。
- 不修改 Flyway 历史迁移；数据库默认值保留为最后一道兼容保护，但应用写入必须显式获得统一审计值。
- 保留用户未跟踪的 `target/`，不得加入提交。

---

### 任务 1： 用架构测试锁定无基类 Repository 合同

**文件：**
- 修改： `server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java`
- 阅读： `server/src/main/java/github/luckygc/am/common/repository/DataRepository.java`
- 阅读： `server/src/main/java/github/luckygc/am/common/repository/package-info.java`

**接口：**
- 强制： 项目 Repository 为直接声明方法的 Jakarta Data 接口。
- 禁止： 项目基类、Jakarta `DataRepository`/`BasicRepository`/`CrudRepository` 继承、无操作注解方法。

- [ ] **步骤 1： 先写失败的架构规则**

替换当前 `project_repositories_should_not_extend_jakarta_crud_repository` 断言；当项目 Repository 出现以下情况时收集违规：

1. 缺少 `jakarta.data.repository.Repository`；
2. 继承任何接口；
3. 自己声明的方法缺少允许的操作注解；
4. 声明 `save`、upsert 或 `@Save`。

错误信息使用：`项目 Repository 必须直接声明实际需要的方法，不得继承基础 Repository`。

- [ ] **步骤 2： 证明规则当前失败**

在 `server/` 下运行：

```bash
mise exec -- mvn -Dtest=ArchitectureRulesTest test
```

预期：测试失败，并列出继承 `github.luckygc.am.common.repository.DataRepository` 的 39 个接口。

- [ ] **步骤 3： 提交测试红灯**

```bash
git add server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java
git commit -m "test: 锁定无基类 Repository 合同"
```

### 任务 2： 逐个迁移 39 个 Jakarta Data Repository

**文件：**
- 修改：附录 A 列出的 39 个 Repository 精确文件
- 删除： `server/src/main/java/github/luckygc/am/common/repository/DataRepository.java`
- 删除： `server/src/main/java/github/luckygc/am/common/repository/package-info.java`

**接口：**
- 每个接口：标注 `@Repository`，删除 `extends DataRepository<...>`。
- 生命周期签名：使用具体实体和 ID 类型；Repository 非空参数使用 `jakarta.annotation.Nonnull`；方法使用显式操作注解。

- [ ] **步骤 1： 按真实调用补齐生命周期方法**

以下清单是继承方法迁移的精确范围；已有自定义方法除导入和注解外保持不变：

| Repository | 需要显式声明的原基类方法 |
| --- | --- |
| ArchiveDataScope | `findById`、`insert`、`update` |
| ArchiveDataScopeDimension | `insert` |
| ArchiveDataScopeSubjectRelation | `insert` |
| ArchiveGovernanceBinding | `insertAll`、`deleteAll` |
| ArchiveGovernanceScheme | `findById`、`insert`、`update`、`delete` |
| ArchiveGovernanceSchemeVersion | `findById`、`insert`、`update` |
| ArchiveGovernanceScope | `insertAll`、`deleteAll` |
| ArchiveItemAudit | `insert` |
| ArchiveItem | 无 |
| ArchiveVolume | 无 |
| ArchiveCategory | `findById`、`insert`、`update`、`delete` |
| ArchiveClassificationScheme | `findById`、`insert`、`update` |
| ArchiveField | `findById`、`insert`、`update`、`delete` |
| ArchiveFieldLayout | `insert`、`update`、`delete` |
| ArchiveFondsCategoryScope | `insertAll`、`deleteAll` |
| ArchiveFonds | `findById`、`insert`、`update`、`delete` |
| ArchiveRetentionPeriod | `findById`、`update` |
| ArchiveSecurityLevel | `findById`、`update` |
| ArchiveOntologyAttributeMapping | `findById`、`insert`、`update`、`delete` |
| ArchiveOntologyAttributeType | `insert`、`update`、`delete` |
| ArchiveOntologyEventType | `findById`、`insert`、`insertAll`、`update`、`delete` |
| ArchiveOntologyObjectType | `findById`、`insert`、`insertAll`、`update`、`delete` |
| ArchiveOntologyRelationType | `insert`、`update`、`delete` |
| ArchiveRuleDefinition | `findById`、`insert`、`update`、`delete` |
| ArchiveRuleEffect | `insertAll`、`update`、`delete` |
| ArchiveRuleTrace | `insert` |
| AuthenticationCapChallenge | `findById`、`insert` |
| AuthenticationCapToken | `insert` |
| AuthenticationLoginLog | `insert` |
| AuthenticationUser | `findById`、`insert`、`update` |
| LoginFailureLimit | `findById` |
| SpringSessionRecord | 无 |
| AuthorizationPermission | 无 |
| AuthorizationRole | `findById`、`insert`、`update`、`delete` |
| AuthorizationRolePermissionRelation | `insert` |
| AuthorizationUserRoleRelation | `insert` |
| OrganizationDepartment | `findById`、`insert`、`update` |
| FileLink | `insert` |
| StorageObject | `findById`、`insert`、`delete` |

修改前，使用生产和测试调用点复核清单：

```bash
rg -n '\.(findById|insert|insertAll|update|updateAll|delete|deleteAll)\(' \
  server/src/main/java server/src/test/java
```

如果清单中的接口还有额外真实调用，应声明对应具体方法；不得为追求对称而增加未使用的生命周期方法。

- [ ] **步骤 2： 使用实体具体类型声明方法**

例如，`ArchiveFondsDataRepository` 应包含等价的具体签名：

```java
@Repository
public interface ArchiveFondsDataRepository {

    @Insert
    ArchiveFonds insert(@Nonnull ArchiveFonds entity);

    @Update
    ArchiveFonds update(@Nonnull ArchiveFonds entity);

    @Find
    Optional<ArchiveFonds> findById(@By(By.ID) @Nonnull Long id);

    @Delete
    void delete(@Nonnull ArchiveFonds entity);
}
```

批量方法使用 `List<ConcreteEntity>`，不保留泛型 `<S extends T>` 签名。

- [ ] **步骤 3： 删除自定义基类并运行聚焦测试**

```bash
rg -n 'common\.repository\.DataRepository|extends (DataRepository|BasicRepository|CrudRepository)' \
  server/src/main/java && exit 1 || true
mise exec -- mvn -Dtest=ArchitectureRulesTest test
mise exec -- mvn -DskipTests compile
```

预期：搜索不输出内容；架构测试和编译通过。

- [ ] **步骤 4： 格式化并提交 Repository 迁移**

```bash
mise exec -- mvn spotless:apply
git diff --check
git add -A server/src/main/java/github/luckygc/am/common/repository
git add \
  server/src/main/java/github/luckygc/am/module \
  server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java
git commit -m "refactor: 移除自定义 Repository 基类"
```

### 任务 3： 建立共享审计上下文

**文件：**
- 新建： `server/src/main/java/github/luckygc/am/infrastructure/audit/AuditContext.java`
- 新建： `server/src/main/java/github/luckygc/am/infrastructure/audit/AuditContextProvider.java`
- 新建： `server/src/main/java/github/luckygc/am/infrastructure/audit/AuditConfiguration.java`
- 新建： `server/src/main/java/github/luckygc/am/infrastructure/audit/package-info.java`
- 新建： `server/src/test/java/github/luckygc/am/infrastructure/audit/AuditContextProviderTests.java`
- 新建： `server/src/test/java/github/luckygc/am/infrastructure/audit/package-info.java`

**接口：**
- `record AuditContext(LocalDateTime now, @Nullable Long userId)`
- `AuditContext AuditContextProvider.current()`
- `Clock auditClock()` 默认返回 `Clock.systemDefaultZone()`，测试中可以替换。

- [ ] **步骤 1： 先写上下文测试**

使用固定 `Clock` 覆盖以下情况：

1. 已认证 `AuthenticatedUser` 返回固定 `now` 和用户 ID；
2. 无认证时返回固定 `now` 和 null `userId`；
3. anonymous or unauthenticated authentication returns null `userId`;
4. 不支持的 principal 返回 null，不导致后台任务失败。

运行：

```bash
mise exec -- mvn -Dtest=AuditContextProviderTests test
```

预期：由于生产类尚不存在，编译或测试失败。

- [ ] **步骤 2： 实现最小共享上下文**

`AuditContextProvider.current()` 调用一次 `LocalDateTime.now(clock)`，读取 `SecurityContextHolder`，并将可识别 principal 交给 `AuthenticatedUsers.currentUserId`。不得跨数据库操作缓存上下文。

- [ ] **步骤 3： 运行并提交**

```bash
mise exec -- mvn -Dtest=AuditContextProviderTests test
mise exec -- mvn spotless:apply
git add server/src/main/java/github/luckygc/am/infrastructure/audit \
  server/src/test/java/github/luckygc/am/infrastructure/audit
git commit -m "feat: 提供统一审计上下文"
```

### 任务 4： 让 Hibernate 拦截器统一写入时间和用户

**文件：**
- 新建： `server/src/main/java/github/luckygc/am/common/audit/CreationTimeAuditable.java`
- 新建： `server/src/main/java/github/luckygc/am/common/audit/UpdateTimeAuditable.java`
- 修改： `server/src/main/java/github/luckygc/am/common/audit/CreationAuditable.java`
- 修改： `server/src/main/java/github/luckygc/am/common/audit/UpdateAuditable.java`
- 修改： `server/src/main/java/github/luckygc/am/infrastructure/hibernate/SecurityAuditingInterceptor.java`
- 新建： `server/src/test/java/github/luckygc/am/infrastructure/hibernate/SecurityAuditingInterceptorTests.java`
- 修改：附录 B 列出的 38 个实体精确文件

**接口：**
- `CreationTimeAuditable`: `getCreatedAt` / `setCreatedAt`.
- `UpdateTimeAuditable`: `getUpdatedAt` / `setUpdatedAt`.
- `CreationAuditable extends CreationTimeAuditable`: nullable `createdBy` getter/setter.
- `UpdateAuditable extends UpdateTimeAuditable`: nullable `updatedBy` getter/setter.
- 拦截器 insert：始终设置创建字段；实体支持更新字段时同时设置更新字段。
- 拦截器 update：始终设置更新字段。

- [ ] **步骤 1： 先写 Hibernate 拦截器测试**

使用模拟的 `AuditContextProvider` 和简单测试审计对象，断言：

1. insert 覆盖调用方传入的 `createdAt`、`createdBy`、`updatedAt`、`updatedBy`；
2. update 覆盖调用方传入的 `updatedAt` 和 `updatedBy`；
3. null `userId` writes null user fields while still writing time;
4. 只有时间字段的实体无需用户字段也能获得时间；
5. state 数组值与实体属性保持一致。

运行：

```bash
mise exec -- mvn -Dtest=SecurityAuditingInterceptorTests test
```

预期：拦截器仍直接读取安全上下文且只按条件填充用户字段，因此测试失败。

- [ ] **步骤 2： 实现审计接口和拦截器**

将时间访问器移入两个纯时间接口。用户 getter、setter 参数、实体用户字段和相关泛型类型位置使用 `org.jspecify.annotations.Nullable` 标注。向 `SecurityAuditingInterceptor` 注入 `AuditContextProvider`，删除对 Spring Security 的直接访问。

每次回调只获取一个上下文，同时更新 Java 对象和匹配的 Hibernate `state` 槽位。通用审计字段不信任调用方传值。

- [ ] **步骤 3： 迁移全部固定实体**

对以下命令返回的每个实体：

```bash
rg -l '@CreationTimestamp|@UpdateTimestamp' server/src/main/java
```

删除 Hibernate 时间戳注解和导入，并实现最窄的匹配审计接口：

- 同时具有时间和用户字段的实体：`CreationAuditable` 和/或 `UpdateAuditable`；
- 只有时间字段的实体：`CreationTimeAuditable` 和/或 `UpdateTimeAuditable`。

不得只为追求接口对称而向实体或迁移增加原本不存在的用户字段。

- [ ] **步骤 4： 增加架构防回退规则**

增加 ArchUnit 断言：项目实体不得依赖 `org.hibernate.annotations.CreationTimestamp` 或 `org.hibernate.annotations.UpdateTimestamp`；Hibernate 审计拦截器必须依赖 `AuditContextProvider`，不得依赖 `SecurityContextHolder`。

- [ ] **步骤 5： 验证并提交 Hibernate 审计**

```bash
rg -n '@CreationTimestamp|@UpdateTimestamp' server/src/main/java && exit 1 || true
mise exec -- mvn -Dtest=SecurityAuditingInterceptorTests,ArchitectureRulesTest,ServerApplicationTests test
mise exec -- mvn spotless:apply
git diff --check
git add server/src/main/java/github/luckygc/am/common/audit \
  server/src/main/java/github/luckygc/am/infrastructure/hibernate \
  server/src/main/java/github/luckygc/am/module \
  server/src/test/java/github/luckygc/am/infrastructure/hibernate \
  server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java \
  server/src/test/java/github/luckygc/am/ServerApplicationTests.java
git commit -m "refactor: 统一 Hibernate 实体审计写入"
```

### 任务 5： 用 MyBatis 插件注入保留审计参数

**文件：**
- 新建： `server/src/main/java/github/luckygc/am/infrastructure/mybatis/MyBatisAuditingInterceptor.java`
- 新建： `server/src/main/java/github/luckygc/am/infrastructure/mybatis/package-info.java`
- 新建： `server/src/test/java/github/luckygc/am/infrastructure/mybatis/MyBatisAuditingInterceptorTests.java`
- 新建： `server/src/test/java/github/luckygc/am/infrastructure/mybatis/package-info.java`

**接口：**
- 拦截：`Executor.update(MappedStatement, Object)`，以及 returning 语句使用的两个 MyBatis `Executor.query` 重载。
- 保留键：`_audit`。
- 值：当前 `AuditContext`，覆盖调用方提供的任何 `_audit`。

- [ ] **步骤 1： 先写 MyBatis 插件测试**

使用模拟的 `Invocation`、`MappedStatement` 和 `AuditContextProvider` 测试：

1. 可变参数 Map 在 `proceed()` 前获得 `_audit`；
2. a forged `_audit` value is overwritten;
3. query 与 update 路径行为一致；
4. null 参数和非 Map 参数不修改并继续执行；
5. plugin never changes SQL text or constructs a new `BoundSql`.

运行：

```bash
mise exec -- mvn -Dtest=MyBatisAuditingInterceptorTests test
```

预期：拦截器尚不存在，因此编译失败。

- [ ] **步骤 2： 实现参数注入插件**

使用 MyBatis `@Intercepts`/`@Signature` 和 Spring `@Component`。只修改 `Map<String, Object>` 参数；需要审计的 Mapper 写方法因此必须暴露至少一个 `@Param`。仅对支持的可变 Map 调用 `provider.current()`，写入 `_audit` 后调用 `invocation.proceed()`。

不要注册第二个插件配置 Bean；MyBatis Spring Boot starter 会发现该组件。

- [ ] **步骤 3： 运行并提交插件**

```bash
mise exec -- mvn -Dtest=MyBatisAuditingInterceptorTests test
mise exec -- mvn spotless:apply
git add server/src/main/java/github/luckygc/am/infrastructure/mybatis \
  server/src/test/java/github/luckygc/am/infrastructure/mybatis
git commit -m "feat: 注入 MyBatis 统一审计参数"
```

### 任务 6： 迁移 MyBatis 写入 SQL 到 `_audit`

**文件：**
- 修改： `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveMapper.java`
- 修改： `server/src/main/resources/mapper/archive/ArchiveMapper.xml`
- 修改： `server/src/main/java/github/luckygc/am/module/authentication/mapper/LoginFailureLimitMapper.java`
- 修改： `server/src/main/resources/mapper/authentication/LoginFailureLimitMapper.xml`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemCommandService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemElectronicFileService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineTableService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemLockService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveVolumeService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintService.java`
- 修改： `server/src/main/java/github/luckygc/am/module/authentication/service/LoginFailureLimitService.java`
- 修改： `server/src/test/java/github/luckygc/am/module/authentication/LoginFailureLimitIntegrationTests.java`
- 修改： `server/src/test/java/github/luckygc/am/module/authentication/service/LoginFailureLimitServiceTests.java`
- 修改： `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemAuditWriteTests.java`
- 修改： `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemElectronicFileServiceTests.java`
- 修改： `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveNoUniquenessTests.java`
- 修改： `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveVolumePermissionTests.java`
- 修改： `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`
- 新建： `server/src/test/java/github/luckygc/am/infrastructure/mybatis/MyBatisAuditingIntegrationTests.java`

**接口：**
- XML 引用：`#{_audit.now}` 和 `#{_audit.userId}`。
- 删除只服务通用审计的 Java 参数。
- 业务操作人参数保留 `deletedBy`、`lockedBy`、`requestedBy` 等语义名称。

- [ ] **步骤 1： 先写数据库集成测试**

使用 PostgreSQL 容器和固定审计时钟，覆盖一次普通 `Executor.update` 写入和一次 query/`RETURNING` 写入。即使调用方尝试提供伪造 `_audit`，也应断言持久化的 `created_at`、`updated_at`、`created_by` 和 `updated_by` 等于注入上下文。

同时覆盖未认证执行：时间字段已设置，可空用户审计字段保持 null。

运行：

```bash
mise exec -- mvn -Dtest=MyBatisAuditingIntegrationTests test
```

预期：Mapper XML 仍使用方法参数 `userId` 和数据库 `localtimestamp`，因此测试失败。

- [ ] **步骤 2： 迁移通用审计字段**

在两份 Mapper XML 中：

- 将通用 `created_at`/`updated_at` 值和赋值替换为 `#{_audit.now}`；
- 将通用 `created_by`/`updated_by` 替换为 `#{_audit.userId}`；
- 动态组装 insert 的当前列清单遗漏审计列时，显式增加审计列；
- 确保 insert 和 update 语句通过参数 Map 调用，使插件可以注入 `_audit`。

为 `LoginFailureLimitMapper.update` 增加 `@Param("limit")`，先把 XML 属性路径从 `#{property}` 改为 `#{limit.property}`，再引用 `_audit`。

- [ ] **步骤 3： 区分业务操作人与通用审计人**

使用以下判定表：

| 字段语义 | 处理 |
| --- | --- |
| `created_by`、`updated_by` | 删除 audit-only `userId` 参数，改用 `_audit.userId` |
| `deleted_by` | 保留显式参数并重命名为 `deletedBy`；同一 SQL 的 `updated_by` 使用 `_audit.userId` |
| `locked_by` | 保留 `lockedBy` |
| 解锁、删除、规则执行的业务发起人 | 保留语义参数，不借用 `_audit` |
| 存储对象/文件链接所有者 | 保留业务赋值，不改成当前审计用户 |

完成前搜索每个受影响语句：

```bash
rg -n 'created_at|updated_at|created_by|updated_by|deleted_by|locked_by|localtimestamp' \
  server/src/main/resources/mapper
```

预期： generic audit fields reference `_audit`; any remaining `localtimestamp` is documented as a non-generic business timestamp.

- [ ] **步骤 4： 更新调用方和测试**

只删除纯粹服务通用审计列的方法参数。同步更新 Mockito 验证和 Mapper 集成测试以匹配收窄后的签名。不得删除权限、所有权或业务审计流水使用的用户 ID。

- [ ] **步骤 5： 验证并提交 MyBatis SQL 迁移**

```bash
mise exec -- mvn -Dtest=MyBatisAuditingInterceptorTests,MyBatisAuditingIntegrationTests,LoginFailureLimitIntegrationTests test
mise exec -- mvn -DskipTests compile
mise exec -- mvn spotless:apply
git diff --check
git add server/src/main/java/github/luckygc/am/module \
  server/src/main/resources/mapper \
  server/src/test/java/github/luckygc/am/infrastructure/mybatis \
  server/src/test/java/github/luckygc/am/module
git commit -m "refactor: 统一 MyBatis 审计字段写入"
```

### 任务 7： 执行后端全量验证和回归审计

**文件：**
- 阅读并验证：任务 1–6 修改的文件；最终验证期间不得扩大范围。

**接口：**
- 验证： Repository 合同、双持久化审计合同、格式、编译和完整后端测试。

- [ ] **步骤 1： 执行静态回归搜索**

```bash
rg -n 'common\.repository\.DataRepository|extends (DataRepository|BasicRepository|CrudRepository)' \
  server/src && exit 1 || true
rg -n '@CreationTimestamp|@UpdateTimestamp' server/src && exit 1 || true
rg -n 'SecurityContextHolder|LocalDateTime\.now|Instant\.now' \
  server/src/main/java/github/luckygc/am/infrastructure/{hibernate,mybatis} && exit 1 || true
```

预期：三次搜索均不输出内容。

- [ ] **步骤 2： 执行完整 Maven 验证**

从仓库根目录运行：

```bash
task server-format-check
task server-compile
task server-test
```

预期：所有命令退出码为 0。如果 PostgreSQL/Docker 不可用导致 Testcontainers 无法启动，记录准确失败测试名和环境依赖，不得声称已完成全量验证。

- [ ] **步骤 3： 审查最终差异**

```bash
git diff --check
git status --short
git diff --stat HEAD~6..HEAD
```

预期：没有空白错误；只存在计划内 Repository/审计文件和用户原有的未跟踪 `target/`。

## 附录 A: 39 个 Repository 精确文件清单

- `server/src/main/java/github/luckygc/am/module/archive/authorization/repository/ArchiveDataScopeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/authorization/repository/ArchiveDataScopeDimensionDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/authorization/repository/ArchiveDataScopeSubjectRelationDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/repository/ArchiveGovernanceBindingDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/repository/ArchiveGovernanceSchemeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/repository/ArchiveGovernanceSchemeVersionDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/repository/ArchiveGovernanceScopeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/item/repository/ArchiveItemAuditDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/item/repository/ArchiveItemDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/item/repository/ArchiveVolumeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveCategoryDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveClassificationSchemeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveFieldDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveFieldLayoutDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveFondsCategoryScopeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveFondsDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveRetentionPeriodDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveSecurityLevelDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/repository/ArchiveOntologyAttributeMappingDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/repository/ArchiveOntologyAttributeTypeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/repository/ArchiveOntologyEventTypeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/repository/ArchiveOntologyObjectTypeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/repository/ArchiveOntologyRelationTypeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/rule/repository/ArchiveRuleDefinitionDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/rule/repository/ArchiveRuleEffectDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/archive/rule/repository/ArchiveRuleTraceDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authentication/repository/AuthenticationCapChallengeDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authentication/repository/AuthenticationCapTokenDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authentication/repository/AuthenticationLoginLogDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authentication/repository/AuthenticationUserDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authentication/repository/LoginFailureLimitDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authentication/repository/SpringSessionRecordDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authorization/repository/AuthorizationPermissionDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authorization/repository/AuthorizationRoleDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authorization/repository/AuthorizationRolePermissionRelationDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/authorization/repository/AuthorizationUserRoleRelationDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/organization/repository/OrganizationDepartmentDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/storage/repository/FileLinkDataRepository.java`
- `server/src/main/java/github/luckygc/am/module/storage/repository/StorageObjectDataRepository.java`

## 附录 B: 38 个时间审计实体精确文件清单

- `server/src/main/java/github/luckygc/am/module/archive/authorization/ArchiveDataScope.java`
- `server/src/main/java/github/luckygc/am/module/archive/authorization/ArchiveDataScopeDimension.java`
- `server/src/main/java/github/luckygc/am/module/archive/authorization/ArchiveDataScopeSubjectRelation.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/ArchiveGovernanceBinding.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/ArchiveGovernanceScheme.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/ArchiveGovernanceSchemeVersion.java`
- `server/src/main/java/github/luckygc/am/module/archive/governance/ArchiveGovernanceScope.java`
- `server/src/main/java/github/luckygc/am/module/archive/item/ArchiveItem.java`
- `server/src/main/java/github/luckygc/am/module/archive/item/ArchiveItemAudit.java`
- `server/src/main/java/github/luckygc/am/module/archive/item/ArchiveVolume.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveCategory.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveClassificationScheme.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveField.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveFieldLayout.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveFonds.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveFondsCategoryScope.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveRetentionPeriod.java`
- `server/src/main/java/github/luckygc/am/module/archive/metadata/ArchiveSecurityLevel.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/ArchiveOntologyAttributeMapping.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/ArchiveOntologyAttributeType.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/ArchiveOntologyEventType.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/ArchiveOntologyObjectType.java`
- `server/src/main/java/github/luckygc/am/module/archive/ontology/ArchiveOntologyRelationType.java`
- `server/src/main/java/github/luckygc/am/module/archive/rule/ArchiveRuleDefinition.java`
- `server/src/main/java/github/luckygc/am/module/archive/rule/ArchiveRuleEffect.java`
- `server/src/main/java/github/luckygc/am/module/archive/rule/ArchiveRuleTrace.java`
- `server/src/main/java/github/luckygc/am/module/authentication/AuthenticationCapChallenge.java`
- `server/src/main/java/github/luckygc/am/module/authentication/AuthenticationCapToken.java`
- `server/src/main/java/github/luckygc/am/module/authentication/AuthenticationLoginLog.java`
- `server/src/main/java/github/luckygc/am/module/authentication/AuthenticationUser.java`
- `server/src/main/java/github/luckygc/am/module/authentication/LoginFailureLimit.java`
- `server/src/main/java/github/luckygc/am/module/authorization/AuthorizationPermission.java`
- `server/src/main/java/github/luckygc/am/module/authorization/AuthorizationRole.java`
- `server/src/main/java/github/luckygc/am/module/authorization/AuthorizationRolePermissionRelation.java`
- `server/src/main/java/github/luckygc/am/module/authorization/AuthorizationUserRoleRelation.java`
- `server/src/main/java/github/luckygc/am/module/organization/OrganizationDepartment.java`
- `server/src/main/java/github/luckygc/am/module/storage/FileLink.java`
- `server/src/main/java/github/luckygc/am/module/storage/StorageObject.java`
