# 档案分类编码永久全局唯一实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修正首轮实现的生命周期和异常边界，使分类编码创建后不可修改、逻辑删除后不可复用，并且只有指定唯一索引冲突返回 `409 / ALREADY_EXISTS`。

**Architecture:** `category_code` 是档案主表和动态表共同依赖的永久路由键，数据库使用覆盖全部历史分类的普通唯一索引。Service 在创建时提前检查活动冲突、在修改时拒绝改名，并只识别 Hibernate 异常链中指定约束名；真实 PostgreSQL + Service 集成测试验证已删除编码仍不能复用。

**Tech Stack:** Java 25、Spring Boot、Jakarta Data、Hibernate StatelessSession、PostgreSQL、Flyway、JUnit 5、Mockito、Testcontainers、OpenSpec、Maven、Spotless。

## Global Constraints

- 始终使用中文交流、文档和测试显示名。
- 当前项目未正式发布，直接维护目标 Flyway 结构，不增加兼容迁移。
- 分类编码在全部历史分类中永久全局唯一，逻辑删除不释放编码。
- 分类编码创建后不可修改；不改变动态表命名、档案主表字段或查询链路。
- 编码最大 100 字符，名称最大 255 字符；超限在写库前返回 `400 / INVALID_ARGUMENT`。
- 只有约束名 `uk_am_archive_category_code` 的数据库冲突映射为 `409 / ALREADY_EXISTS`，其他完整性异常原样抛出。
- 保留已新增的显式 `ArchiveCategoryDataRepository.findByCategoryCode`，不实施通用 Repository 基类移除或审计统一。

---

### Task 1: 修复分类编码生命周期与异常边界

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`
- Modify: `server/src/test/java/github/luckygc/am/ServerApplicationTests.java`
- Modify: `server/src/test/java/github/luckygc/am/infrastructure/web/GlobalExceptionHandlerTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveCategoryService.java`
- Modify: `server/src/main/resources/db/migration/V20260622_0100__create_archive_tables.sql`
- Modify: `openspec/specs/archive-classification-scheme/spec.md`
- Modify: `openspec/specs/archive-metadata/spec.md`
- Modify: `.superpowers/sdd/category-code-uniqueness-task-1-report.md`（scratch，不提交）
- Modify: `.superpowers/sdd/openspec-task-2-report.md`（scratch，不提交）

**Interfaces:**
- Consumes: `ArchiveCategoryDataRepository.findByCategoryCode(String)`、`ArchiveCategoryRequest`、Hibernate `ConstraintViolationException.getConstraintName()`、`GlobalExceptionHandler` 的现有状态码映射。
- Produces: 永久唯一索引 `uk_am_archive_category_code`；不可修改的分类编码；约束名精确识别；`409 / ALREADY_EXISTS` ProblemDetail；一致的长期规格。

- [ ] **Step 1: 将首轮测试改成新的失败合同**

在 `ArchiveMetadataServiceTests` 保留“创建活动重复编码返回 409”和“修改时保留自身编码”，删除“修改成其他分类编码返回 409”与无约束名的宽泛异常测试，增加以下测试。测试使用现有 `scheme(...)`、`category(...)` helper，并补充 `java.sql.SQLException`、Hibernate `ConstraintViolationException` 静态所需导入。

```java
@Test
@DisplayName("拒绝修改分类编码")
void updateCategoryShouldRejectCategoryCodeChange() {
    ArchiveCategory current = category(12L, ArchiveManagementMode.ITEM_ONLY);
    current.setSchemeId(8L);
    when(categoryRepository.findById(12L)).thenReturn(Optional.of(current));

    assertThatThrownBy(
                    () ->
                            categoryService.updateCategory(
                                    12L,
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            "project",
                                            "项目档案",
                                            null,
                                            ArchiveManagementMode.ITEM_ONLY,
                                            true,
                                            0),
                                    9L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(
                    exception ->
                            assertThat(((ResponseStatusException) exception).getStatusCode())
                                    .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST))
            .hasMessageContaining("分类编码创建后不可修改");

    verify(categoryRepository, never()).update(any(ArchiveCategory.class));
}

@Test
@DisplayName("修改不存在分类时优先返回资源不存在")
void updateCategoryShouldLoadTargetBeforeValidatingCode() {
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
                    () ->
                            categoryService.updateCategory(
                                    99L,
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            "occupied",
                                            "不存在分类",
                                            null,
                                            ArchiveManagementMode.ITEM_ONLY,
                                            true,
                                            0),
                                    9L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(
                    exception ->
                            assertThat(((ResponseStatusException) exception).getStatusCode())
                                    .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
}
```

长度校验使用精确边界：

```java
@Test
@DisplayName("创建分类时拒绝超长编码和名称")
void createCategoryShouldRejectOversizedCodeAndName() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));

    assertThatThrownBy(
                    () ->
                            categoryService.createCategory(
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            "c".repeat(101),
                                            "合同档案",
                                            null,
                                            ArchiveManagementMode.ITEM_ONLY,
                                            true,
                                            0),
                                    9L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("分类编码长度不能超过 100");

    assertThatThrownBy(
                    () ->
                            categoryService.createCategory(
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            "contract",
                                            "名".repeat(256),
                                            null,
                                            ArchiveManagementMode.ITEM_ONLY,
                                            true,
                                            0),
                                    9L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("分类名称长度不能超过 255");

    verify(categoryRepository, never()).insert(any(ArchiveCategory.class));
}
```

用真实约束名构造 Hibernate 异常链，并验证其他完整性异常透传：

```java
@Test
@DisplayName("指定分类编码唯一约束冲突返回资源已存在")
void createCategoryShouldMapNamedUniqueConstraint() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
    when(categoryRepository.findByCategoryCode("contract")).thenReturn(null);
    var constraintViolation =
            new org.hibernate.exception.ConstraintViolationException(
                    "duplicate",
                    new SQLException("duplicate"),
                    "uk_am_archive_category_code");
    when(categoryRepository.insert(any(ArchiveCategory.class)))
            .thenThrow(
                    new org.springframework.dao.DataIntegrityViolationException(
                            "duplicate", constraintViolation));

    assertThatThrownBy(
                    () ->
                            categoryService.createCategory(
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            "contract",
                                            "合同档案",
                                            null,
                                            ArchiveManagementMode.ITEM_ONLY,
                                            true,
                                            0),
                                    9L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(
                    exception ->
                            assertThat(((ResponseStatusException) exception).getStatusCode())
                                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT))
            .hasMessageContaining("分类编码已存在");
}

@Test
@DisplayName("非分类编码约束的完整性异常保持原样")
void createCategoryShouldRethrowUnrelatedIntegrityViolation() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
    when(categoryRepository.findByCategoryCode("contract")).thenReturn(null);
    var unrelated =
            new org.springframework.dao.DataIntegrityViolationException(
                    "other",
                    new org.hibernate.exception.ConstraintViolationException(
                            "other", new SQLException("other"), "fk_am_archive_category_scheme"));
    when(categoryRepository.insert(any(ArchiveCategory.class))).thenThrow(unrelated);

    assertThatThrownBy(
                    () ->
                            categoryService.createCategory(
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            "contract",
                                            "合同档案",
                                            null,
                                            ArchiveManagementMode.ITEM_ONLY,
                                            true,
                                            0),
                                    9L))
            .isSameAs(unrelated);
}
```

- [ ] **Step 2: 修订 PostgreSQL 集成测试为永久唯一**

将 `ServerApplicationTests.categoryCodeIsGloballyUniqueAndReusableAfterSoftDelete` 改名为 `categoryCodeRemainsGloballyUniqueAfterSoftDelete`。第一条活动分类插入后，跨方案重复插入仍应失败；将第一条标记为删除后，先断言真实 Repository 查询返回 `null`，再通过真实 `ArchiveCategoryService.createCategory(...)` 断言历史编码冲突仍返回 `409`：

```java
jdbcTemplate.update(
        "update am_archive_category set deleted_flag = true "
                + "where scheme_id = ? and category_code = ?",
        firstSchemeId,
        categoryCode);
Assertions.assertNull(archiveCategoryDataRepository.findByCategoryCode(categoryCode));

ResponseStatusException exception =
        Assertions.assertThrows(
                ResponseStatusException.class,
                () ->
                        archiveCategoryService.createCategory(
                                new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                        secondSchemeId,
                                        categoryCode,
                                        "历史编码不可复用",
                                        null,
                                        ArchiveManagementMode.ITEM_ONLY,
                                        true,
                                        0),
                                9L));
Assertions.assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
```

为测试类增加 `ArchiveCategoryDataRepository` 和 `ArchiveCategoryService` 注入。索引断言改为：

```java
Assertions.assertEquals(
        "uk_am_archive_category_code",
        jdbcTemplate.queryForObject(
                "select to_regclass('uk_am_archive_category_code')::text", String.class));
Assertions.assertNull(
        jdbcTemplate.queryForObject(
                "select to_regclass('uk_am_archive_category_scheme_code_active')::text",
                String.class));
Assertions.assertNull(
        jdbcTemplate.queryForObject(
                "select to_regclass('uk_am_archive_category_code_active')::text", String.class));
Assertions.assertFalse(uniqueIndexUsesActiveRowsOnly("uk_am_archive_category_code"));
```

保留 fixture 的 `finally` 清理，确保失败时也按“分类后、方案前”物理删除测试数据。

- [ ] **Step 3: 增加 ProblemDetail 合同失败测试**

在 `GlobalExceptionHandlerTests` 增加：

```java
@Test
@DisplayName("资源冲突异常输出已存在 ProblemDetail")
void conflictUsesAlreadyExistsProblemDetail() {
    MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/api/v1/archive-categories");

    var response =
            handler.handleResponseStatusException(
                    new ResponseStatusException(HttpStatus.CONFLICT, "分类编码已存在"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(409);
    assertThat(response.getBody().getProperties())
            .containsEntry("code", "ALREADY_EXISTS")
            .containsEntry("reason", "ALREADY_EXISTS_ERROR")
            .containsEntry("path", "/api/v1/archive-categories");
}
```

- [ ] **Step 4: 运行修复前红灯**

```bash
cd server
mise exec -- mvn -q -Dtest=ArchiveMetadataServiceTests,GlobalExceptionHandlerTests test
mise exec -- mvn -q -Dtest=ServerApplicationTests test
```

预期：Service 测试因仍允许改名、缺少长度校验、宽泛捕获异常而失败；PostgreSQL 18 Testcontainers 实跑时因部分唯一索引允许删除后复用、旧索引名仍存在而失败。ProblemDetail 测试预计已通过，用于固定现有映射。

- [ ] **Step 5: 实现不可变编码、长度校验和约束名识别**

在 `ArchiveCategoryService` 定义：

```java
private static final String CATEGORY_CODE_UNIQUE_CONSTRAINT =
        "uk_am_archive_category_code";
private static final int CATEGORY_CODE_MAX_LENGTH = 100;
private static final int CATEGORY_NAME_MAX_LENGTH = 255;
```

创建路径使用 `requireCategoryCode`、`requireCategoryName`，再调用 `findByCategoryCode` 提前检查活动冲突。修改路径必须先 `findById`，然后校验请求编码等于当前编码，不再调用 Repository 按编码查询：

```java
ArchiveCategory category =
        categoryRepository.findById(id).orElseThrow(() -> notFound("档案分类不存在"));
ArchiveClassificationScheme scheme = loadEnabledClassificationScheme(request.schemeId());
String categoryCode = requireCategoryCode(request.categoryCode());
if (!category.getCategoryCode().equals(categoryCode)) {
    throw badRequest("分类编码创建后不可修改");
}
String categoryName = requireCategoryName(request.categoryName());
```

输入核心方法：

```java
private String requireCategoryCode(@Nullable String categoryCode) {
    String normalized = StringUtils.trimToNull(categoryCode);
    if (normalized == null) {
        throw badRequest("分类编码不能为空");
    }
    if (StringUtils.length(normalized) > CATEGORY_CODE_MAX_LENGTH) {
        throw badRequest("分类编码长度不能超过 100");
    }
    return normalized;
}

private String requireCategoryName(@Nullable String categoryName) {
    String normalized = StringUtils.trimToNull(categoryName);
    if (normalized == null) {
        throw badRequest("分类名称不能为空");
    }
    if (StringUtils.length(normalized) > CATEGORY_NAME_MAX_LENGTH) {
        throw badRequest("分类名称长度不能超过 255");
    }
    return normalized;
}
```

创建写入只转换指定约束名；修改写入不再捕获 `DataIntegrityViolationException`：

```java
try {
    return mapCategory(categoryRepository.insert(category));
} catch (DataIntegrityViolationException exception) {
    if (isCategoryCodeUniqueViolation(exception)) {
        throw categoryCodeConflict(exception);
    }
    throw exception;
}
```

异常链识别：

```java
private boolean isCategoryCodeUniqueViolation(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
        if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                && CATEGORY_CODE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
            return true;
        }
        current = current.getCause();
    }
    return false;
}
```

删除不再使用的 `Objects` 导入、旧 `requireUniqueCategoryCode` 和无参 `categoryCodeConflict()`。保留带 cause 的冲突方法。

- [ ] **Step 6: 将 Flyway 改为永久唯一索引**

把当前迁移中的：

```sql
create unique index uk_am_archive_category_code_active
    on am_archive_category (category_code)
    where deleted_flag = false;
```

替换为：

```sql
create unique index uk_am_archive_category_code
    on am_archive_category (category_code);
```

不新增迁移、不保留部分索引、不硬编码 schema。

- [ ] **Step 7: 校准长期规格和报告**

`archive-classification-scheme` 改为：

```markdown
- **AND** 分类编码 SHALL 在全部历史分类中永久唯一
```

`archive-metadata` 的分类创建与冲突场景改为：

```markdown
- **AND** 分类编码 SHALL 在全部历史分类中永久唯一

#### Scenario: 拒绝修改分类编码

- **WHEN** 客户端修改已有分类并提交与当前值不同的分类编码
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 使用 `400 Bad Request` 和 `INVALID_ARGUMENT` ProblemDetail

#### Scenario: 拒绝复用分类编码

- **WHEN** 客户端创建分类并提交任一历史分类已使用的编码
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 使用 `409 Conflict` 和 `ALREADY_EXISTS` ProblemDetail
- **AND** 已逻辑删除分类 SHALL 继续占用分类编码
```

补充编码 100、名称 255 字符边界。更新两个 scratch report，记录首轮审查失败、修订设计提交 `1f6e64a`、本次修复提交和最终验证；不得提交 scratch 文件或修改归档 delta。

- [ ] **Step 8: 格式化并运行定向绿灯验证**

```bash
task server-format
cd server
mise exec -- mvn -q -Dtest=ArchiveMetadataServiceTests,GlobalExceptionHandlerTests test
mise exec -- mvn -q -Dtest=ServerApplicationTests test
cd ..
openspec validate --all --strict --no-interactive
git diff --check
```

预期：Service、异常处理和 PostgreSQL 18 容器测试全部通过；OpenSpec 20/20；diff check 无输出。

- [ ] **Step 9: 运行后端完整验证**

```bash
task server-format-check
task server-compile
task server-test
```

预期：格式、编译和全部后端测试通过；报告实际测试数和 Testcontainers 执行情况。

- [ ] **Step 10: 提交修复**

```bash
git add \
  server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java \
  server/src/test/java/github/luckygc/am/ServerApplicationTests.java \
  server/src/test/java/github/luckygc/am/infrastructure/web/GlobalExceptionHandlerTests.java \
  server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveCategoryService.java \
  server/src/main/resources/db/migration/V20260622_0100__create_archive_tables.sql \
  openspec/specs/archive-classification-scheme/spec.md \
  openspec/specs/archive-metadata/spec.md
git commit -m "fix: 固化档案分类编码生命周期"
git show --check HEAD
git status --short
```

预期：提交检查通过，工作区干净。
