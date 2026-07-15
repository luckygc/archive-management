# 档案分类编码全局唯一实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将档案分类编码收敛为跨分类方案全局唯一，并用 Service 校验、PostgreSQL 部分唯一索引和统一 ProblemDetail 共同守住该不变量。

**Architecture:** 分类方案继续组织分类节点，但 `category_code` 作为动态表路由使用的全局稳定业务键。Repository 提供一个显式按编码查询，Service 提前返回 `409 Conflict / ALREADY_EXISTS`，PostgreSQL 对未删除分类执行最终唯一约束；不改变动态表命名和现有查询链路。

**Tech Stack:** Java 25、Spring Boot、Jakarta Data Repository、Hibernate StatelessSession、PostgreSQL、Flyway、JUnit 5、Mockito、Testcontainers、OpenSpec、Maven、Spotless。

## Global Constraints

- 始终使用中文交流、文档、测试显示名和必要注释。
- 当前项目未正式发布，直接维护目标 Flyway 结构，不增加兼容迁移分支。
- Repository 只新增当前用例真实需要的方法，并使用显式 `@Find` 操作注解；Repository 可空返回使用 `jakarta.annotation.Nullable`。
- 动态表名继续由分类编码、item/volume 对象类型和 `METADATA`/`PHYSICAL` 字段域生成，不加入 `scheme_id` 或数据库自增 ID。
- 未删除分类的 `category_code` 全局唯一；逻辑删除后释放编码。
- 普通重复与并发唯一冲突都返回 `409 Conflict`，由现有全局异常处理映射为 `ALREADY_EXISTS` ProblemDetail。
- 不顺带实施通用 Repository 基类移除或审计拦截器统一任务。

---

### Task 1: 固化分类编码全局唯一不变量

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`
- Modify: `server/src/test/java/github/luckygc/am/ServerApplicationTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveCategoryDataRepository.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveCategoryService.java`
- Modify: `server/src/main/resources/db/migration/V20260622_0100__create_archive_tables.sql`
- Modify: `openspec/specs/archive-classification-scheme/spec.md`
- Modify: `openspec/specs/archive-metadata/spec.md`
- Modify: `.superpowers/sdd/openspec-task-2-report.md`（scratch，不提交）

**Interfaces:**
- Consumes: `ArchiveCategoryRequest.categoryCode()`、Hibernate `@SoftDelete` 对 Repository 查询的未删除过滤、`GlobalExceptionHandler` 对 `ResponseStatusException(HttpStatus.CONFLICT, ...)` 的 `ALREADY_EXISTS` 映射。
- Produces: `@Nullable ArchiveCategory findByCategoryCode(@Nonnull String categoryCode)`；创建/修改分类的全局编码校验；`uk_am_archive_category_code_active` 部分唯一索引；一致的两份长期规格。

- [ ] **Step 1: 写 Service 层失败测试**

在 `ArchiveMetadataServiceTests` 增加以下四类测试，并补充 Mockito 的 `any`、`never` 等静态导入：

```java
@Test
@DisplayName("拒绝跨分类方案创建重复分类编码")
void createCategoryShouldRejectGloballyDuplicateCode() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    ArchiveCategory existing = category(12L, ArchiveManagementMode.ITEM_ONLY);
    existing.setSchemeId(7L);
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
    when(categoryRepository.findByCategoryCode("contract")).thenReturn(existing);

    assertThatThrownBy(
                    () ->
                            categoryService.createCategory(
                                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                            8L,
                                            " contract ",
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

    verify(categoryRepository, never()).insert(any(ArchiveCategory.class));
}
```

同时增加以下三个完整测试：

```java
@Test
@DisplayName("修改分类时允许保留自身编码")
void updateCategoryShouldAllowKeepingOwnCode() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    ArchiveCategory current = category(12L, ArchiveManagementMode.ITEM_ONLY);
    current.setSchemeId(8L);
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
    when(categoryRepository.findById(12L)).thenReturn(Optional.of(current));
    when(categoryRepository.findByCategoryCode("contract")).thenReturn(current);
    when(categoryRepository.update(any(ArchiveCategory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    ArchiveMetadataTypes.ArchiveCategoryDto response =
            categoryService.updateCategory(
                    12L,
                    new ArchiveMetadataTypes.ArchiveCategoryRequest(
                            8L,
                            " contract ",
                            "合同档案",
                            null,
                            ArchiveManagementMode.ITEM_ONLY,
                            true,
                            0),
                    9L);

    assertThat(response.id()).isEqualTo(12L);
    assertThat(response.categoryCode()).isEqualTo("contract");
}

@Test
@DisplayName("拒绝把分类编码修改为其他分类已占用的编码")
void updateCategoryShouldRejectCodeOwnedByAnotherCategory() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    ArchiveCategory current = category(12L, ArchiveManagementMode.ITEM_ONLY);
    current.setSchemeId(8L);
    current.setCategoryCode("contract");
    ArchiveCategory occupied = category(13L, ArchiveManagementMode.ITEM_ONLY);
    occupied.setSchemeId(7L);
    occupied.setCategoryCode("project");
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
    when(categoryRepository.findById(12L)).thenReturn(Optional.of(current));
    when(categoryRepository.findByCategoryCode("project")).thenReturn(occupied);

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
                                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT))
            .hasMessageContaining("分类编码已存在");

    verify(categoryRepository, never()).update(any(ArchiveCategory.class));
}

@Test
@DisplayName("并发创建触发数据库唯一冲突时返回资源已存在")
void createCategoryShouldMapDatabaseUniqueConflict() {
    ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
    when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
    when(categoryRepository.findByCategoryCode("contract")).thenReturn(null);
    when(categoryRepository.insert(any(ArchiveCategory.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

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
```

- [ ] **Step 2: 写 PostgreSQL 约束失败测试**

在 `ServerApplicationTests` 增加测试，先清理固定测试编码，创建两个临时分类方案，在第一个方案插入活动分类；断言第二个方案插入同编码时抛出 `DataIntegrityViolationException`，将第一条分类标记为逻辑删除后断言第二次插入成功，最后按“分类后、方案前”的顺序清理：

```java
@Test
@DisplayName("分类编码跨方案全局唯一且逻辑删除后可复用")
void categoryCodeIsGloballyUniqueAndReusableAfterSoftDelete() {
    String categoryCode = "GLOBAL_UNIQUE_TEST";
    String firstSchemeCode = "GLOBAL_UNIQUE_SCHEME_1";
    String secondSchemeCode = "GLOBAL_UNIQUE_SCHEME_2";
    deleteCategoryUniquenessFixtures(categoryCode, firstSchemeCode, secondSchemeCode);
    try {
        Long firstSchemeId = insertClassificationScheme(firstSchemeCode);
        Long secondSchemeId = insertClassificationScheme(secondSchemeCode);
        jdbcTemplate.update(
                "insert into am_archive_category "
                        + "(scheme_id, category_code, category_name, management_mode) "
                        + "values (?, ?, '全局唯一测试分类', 'ITEM_ONLY')",
                firstSchemeId,
                categoryCode);

        Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                "insert into am_archive_category "
                                        + "(scheme_id, category_code, category_name, management_mode) "
                                        + "values (?, ?, '重复分类', 'ITEM_ONLY')",
                                secondSchemeId,
                                categoryCode));

        jdbcTemplate.update(
                "update am_archive_category set deleted_flag = true "
                        + "where scheme_id = ? and category_code = ?",
                firstSchemeId,
                categoryCode);
        Assertions.assertEquals(
                1,
                jdbcTemplate.update(
                        "insert into am_archive_category "
                                + "(scheme_id, category_code, category_name, management_mode) "
                                + "values (?, ?, '复用分类', 'ITEM_ONLY')",
                        secondSchemeId,
                        categoryCode));
    } finally {
        deleteCategoryUniquenessFixtures(categoryCode, firstSchemeCode, secondSchemeCode);
    }
}
```

辅助方法必须使用 `current_schema()` 下的未限定表名，不硬编码 `public`：

```java
private Long insertClassificationScheme(String schemeCode) {
    return jdbcTemplate.queryForObject(
            "insert into am_archive_classification_scheme "
                    + "(scheme_code, scheme_name, enabled, default_flag, sort_order) "
                    + "values (?, ?, true, false, 0) returning id",
            Long.class,
            schemeCode,
            schemeCode);
}

private void deleteCategoryUniquenessFixtures(
        String categoryCode, String firstSchemeCode, String secondSchemeCode) {
    jdbcTemplate.update("delete from am_archive_category where category_code = ?", categoryCode);
    jdbcTemplate.update(
            "delete from am_archive_classification_scheme where scheme_code in (?, ?)",
            firstSchemeCode,
            secondSchemeCode);
}
```

同时在 `migratedPostgreSqlResourcesAreAvailable()` 断言新索引存在、旧索引不存在，且新索引使用活动行谓词：

```java
Assertions.assertEquals(
        "uk_am_archive_category_code_active",
        jdbcTemplate.queryForObject(
                "select to_regclass('uk_am_archive_category_code_active')::text", String.class));
Assertions.assertNull(
        jdbcTemplate.queryForObject(
                "select to_regclass('uk_am_archive_category_scheme_code_active')::text",
                String.class));
Assertions.assertTrue(uniqueIndexUsesActiveRowsOnly("uk_am_archive_category_code_active"));
```

- [ ] **Step 3: 运行定向测试并确认红灯原因**

运行：

```bash
cd server
mise exec -- mvn -q -Dtest=ArchiveMetadataServiceTests test
mise exec -- mvn -q -Dtest=ServerApplicationTests test
```

预期：Service 测试因 `findByCategoryCode` 尚不存在而 test-compile 失败；添加仅供编译的接口签名后再次运行时，重复编码测试因 Service 未校验而失败。PostgreSQL 测试在 Docker 可用时因当前 `(scheme_id, category_code)` 索引允许跨方案重复而失败；Docker 不可用时 Testcontainers 测试会跳过，报告必须明确记录。

- [ ] **Step 4: 增加显式 Repository 查询**

在 `ArchiveCategoryDataRepository` 增加 Jakarta Data 显式查询；依赖 `@SoftDelete` 自动排除已删除分类：

```java
@Transactional(readOnly = true)
@Find
@Nullable
ArchiveCategory findByCategoryCode(@Nonnull String categoryCode);
```

导入必须使用 `jakarta.annotation.Nullable`，不得使用 JSpecify 代替 Repository 可空返回标注。

- [ ] **Step 5: 实现 Service 校验与并发冲突映射**

在 `ArchiveCategoryService` 导入 `java.util.Objects` 和 `org.springframework.dao.DataIntegrityViolationException`。创建和修改都先得到规范化编码：

```java
// createCategory
String categoryCode = requireUniqueCategoryCode(request.categoryCode(), null);

// updateCategory
String categoryCode = requireUniqueCategoryCode(request.categoryCode(), id);
```

使用以下私有核心方法，允许修改时复用当前实体编码：

```java
private String requireUniqueCategoryCode(
        @Nullable String categoryCode, @Nullable Long currentCategoryId) {
    if (StringUtils.isBlank(categoryCode)) {
        throw badRequest("分类编码不能为空");
    }
    String normalizedCode = categoryCode.trim();
    ArchiveCategory existing = categoryRepository.findByCategoryCode(normalizedCode);
    if (existing != null && !Objects.equals(existing.getId(), currentCategoryId)) {
        throw categoryCodeConflict();
    }
    return normalizedCode;
}

private ResponseStatusException categoryCodeConflict() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "分类编码已存在");
}

private ResponseStatusException categoryCodeConflict(DataIntegrityViolationException exception) {
    return new ResponseStatusException(HttpStatus.CONFLICT, "分类编码已存在", exception);
}
```

创建和修改写入分别用直接 `try/catch` 包围，不新增通用持久化 helper：

```java
try {
    return mapCategory(categoryRepository.insert(category));
} catch (DataIntegrityViolationException exception) {
    throw categoryCodeConflict(exception);
}
```

修改路径对 `categoryRepository.update(category)` 使用相同映射。其他外键、父子关系和分类方案校验继续在写入前完成，因此该 catch 只负责并发分类编码唯一冲突窗口。

- [ ] **Step 6: 修改 PostgreSQL 目标结构**

在 `V20260622_0100__create_archive_tables.sql` 将：

```sql
create unique index uk_am_archive_category_scheme_code_active
    on am_archive_category (scheme_id, category_code)
    where deleted_flag = false;
```

替换为：

```sql
create unique index uk_am_archive_category_code_active
    on am_archive_category (category_code)
    where deleted_flag = false;
```

不新增迁移文件、不保留旧索引、不硬编码 schema。

- [ ] **Step 7: 校准长期规格**

将 `archive-classification-scheme` 的分类创建场景改为：

```markdown
- **AND** 分类编码 SHALL 在全部未删除分类中唯一
```

在 `archive-metadata` 的“档案分类管理”补充冲突场景：

```markdown
#### Scenario: 拒绝重复分类编码

- **WHEN** 客户端创建分类或将分类编码修改为其他未删除分类已占用的编码
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 使用 `409 Conflict` 和 `ALREADY_EXISTS` ProblemDetail
- **AND** 已逻辑删除分类 SHALL NOT 继续占用分类编码
```

不得修改归档中的历史 delta。同步更新 `.superpowers/sdd/openspec-task-2-report.md`，记录设计提交 `e9da29a`、本任务实现提交、分类编码最终策略及验证结果；scratch 报告不加入 Git。

- [ ] **Step 8: 格式化并运行定向绿灯验证**

运行：

```bash
task server-format
cd server
mise exec -- mvn -q -Dtest=ArchiveMetadataServiceTests test
mise exec -- mvn -q -Dtest=ServerApplicationTests test
cd ..
openspec validate --all --strict --no-interactive
git diff --check
```

预期：Service 测试通过；Docker 可用时 PostgreSQL 唯一约束与逻辑删除复用测试通过；OpenSpec 20/20 通过；diff check 无输出。若 Docker 不可用，必须保留跳过证据并继续运行编译与非容器测试，不得声称集成测试通过。

- [ ] **Step 9: 运行后端完整验证**

运行：

```bash
task server-format-check
task server-compile
task server-test
```

预期：格式、编译和全部后端测试通过；报告实际测试数量以及 Testcontainers 是否执行。

- [ ] **Step 10: 提交原子变更**

```bash
git add \
  server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java \
  server/src/test/java/github/luckygc/am/ServerApplicationTests.java \
  server/src/main/java/github/luckygc/am/module/archive/metadata/repository/ArchiveCategoryDataRepository.java \
  server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveCategoryService.java \
  server/src/main/resources/db/migration/V20260622_0100__create_archive_tables.sql \
  openspec/specs/archive-classification-scheme/spec.md \
  openspec/specs/archive-metadata/spec.md
git commit -m "fix: 约束档案分类编码全局唯一"
```

提交后运行 `git show --check HEAD` 与 `git status --short`；预期提交检查通过且工作区干净。
