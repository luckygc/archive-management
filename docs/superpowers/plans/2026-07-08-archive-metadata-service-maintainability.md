# Archive Metadata Service Maintainability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `ArchiveMetadataService` 内部的字段定义、动态表、字段布局和唯一规则职责拆成包内协作类，同时保持现有 API、DTO 和业务行为不变。

**Architecture:** `ArchiveMetadataService` 继续作为 Controller 和其他模块依赖的元数据门面。新增协作类都放在 `github.luckygc.am.module.archive.metadata.service` 包内，不暴露新公共 API，不引入接口抽象或配置开关。动态表 SQL 继续由 MyBatis `ArchiveMapper` 执行，固定实体表继续由 Jakarta Data Repository 管理。

**Tech Stack:** Java 25, Spring Boot, Jakarta Data Repository, MyBatis, JUnit 5, Mockito, AssertJ, Maven, Spotless。

---

## Scope Check

本计划只覆盖 `ArchiveMetadataService` 的内部可维护性治理。它不修改 OpenSpec、HTTP 路由、前端、数据库迁移或业务功能。设计文档中的四类职责都在本计划内落地：字段定义、动态表、字段布局、唯一规则。

## File Structure

- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionService.java`
  - 字段请求校验、字段默认值、字段类型到 SQL 类型、字段值写入实体。
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableService.java`
  - 动态表名、动态表创建/补列、普通索引、唯一索引、动态表状态更新。
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutService.java`
  - 字段布局读取、默认布局、布局保存、字段 DTO 布局合成。
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintService.java`
  - 唯一规则请求校验、规则字段关系维护、字段 searchable 标记、唯一索引同步。
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
  - 保留对外门面，删除已迁出的私有复杂逻辑，委托协作类。
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintServiceTests.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`
  - 更新构造依赖，保留现有行为断言。

---

### Task 1: Extract Field Definition Rules

**Files:**
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionServiceTests.java`
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionService.java`

- [ ] **Step 1: Write failing tests for field definition rules**

Create `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionServiceTests.java`:

```java
package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldRequest;

@DisplayName("档案字段定义规则")
class ArchiveFieldDefinitionServiceTests {

    private final ArchiveFieldDefinitionService service = new ArchiveFieldDefinitionService();

    @Test
    @DisplayName("文本字段使用默认长度和输入框控件")
    void validateShouldApplyTextDefaults() {
        ArchiveFieldDefinitionService.ArchiveFieldValues values =
                service.validate(
                        new ArchiveFieldRequest(
                                null,
                                null,
                                " title ",
                                " 标题 ",
                                ArchiveFieldType.TEXT,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        assertThat(values.archiveLevel()).isEqualTo(ArchiveLevel.ITEM);
        assertThat(values.fieldScope()).isEqualTo(ArchiveFieldScope.METADATA);
        assertThat(values.fieldCode()).isEqualTo("title");
        assertThat(values.fieldName()).isEqualTo("标题");
        assertThat(values.columnName()).isEqualTo("f_title");
        assertThat(values.textLength()).isEqualTo(500);
        assertThat(values.editControl()).isEqualTo(ArchiveFieldControl.INPUT);
        assertThat(values.listVisible()).isTrue();
        assertThat(values.detailColSpan()).isEqualTo(1);
        assertThat(values.enabled()).isTrue();
    }

    @Test
    @DisplayName("拒绝档案记录固定字段编码")
    void validateShouldRejectReservedRecordFieldCode() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        new ArchiveFieldRequest(
                                                ArchiveLevel.ITEM,
                                                ArchiveFieldScope.METADATA,
                                                "archive_no",
                                                "档号",
                                                ArchiveFieldType.TEXT,
                                                100,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                true,
                                                0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("字段编码属于档案记录固定字段");
    }

    @Test
    @DisplayName("拒绝字段类型和编辑控件不匹配")
    void validateShouldRejectIncompatibleEditControl() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        new ArchiveFieldRequest(
                                                ArchiveLevel.ITEM,
                                                ArchiveFieldScope.METADATA,
                                                "amount",
                                                "金额",
                                                ArchiveFieldType.DECIMAL,
                                                null,
                                                18,
                                                2,
                                                ArchiveFieldControl.TEXTAREA,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                true,
                                                0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("编辑控件与字段类型不匹配");
    }

    @Test
    @DisplayName("字段值可写入实体并生成列名")
    void applyValuesShouldPopulateEntity() {
        ArchiveFieldDefinitionService.ArchiveFieldValues values =
                service.validate(
                        new ArchiveFieldRequest(
                                ArchiveLevel.VOLUME,
                                ArchiveFieldScope.PHYSICAL,
                                "box_no",
                                "盒号",
                                ArchiveFieldType.TEXT,
                                80,
                                null,
                                null,
                                ArchiveFieldControl.INPUT,
                                true,
                                120,
                                5,
                                true,
                                1,
                                6,
                                true,
                                1,
                                7,
                                true,
                                true,
                                true,
                                8));
        ArchiveField field = new ArchiveField();

        service.applyValues(field, 12L, values);

        assertThat(field.getCategoryId()).isEqualTo(12L);
        assertThat(field.getArchiveLevel()).isEqualTo(ArchiveLevel.VOLUME);
        assertThat(field.getFieldScope()).isEqualTo(ArchiveFieldScope.PHYSICAL);
        assertThat(field.getFieldCode()).isEqualTo("box_no");
        assertThat(field.getColumnName()).isEqualTo("f_box_no");
        assertThat(field.isExactSearchable()).isTrue();
        assertThat(field.isDataScopeFilterable()).isTrue();
    }

    @Test
    @DisplayName("SQL 类型按字段配置生成")
    void sqlTypeShouldUseFieldConfiguration() {
        assertThat(service.sqlType(field(ArchiveFieldType.TEXT, 120, null, null)))
                .isEqualTo("varchar(120)");
        assertThat(service.sqlType(field(ArchiveFieldType.INTEGER, null, null, null)))
                .isEqualTo("integer");
        assertThat(service.sqlType(field(ArchiveFieldType.DECIMAL, null, 12, 4)))
                .isEqualTo("numeric(12,4)");
        assertThat(service.sqlType(field(ArchiveFieldType.DATE, null, null, null)))
                .isEqualTo("date");
        assertThat(service.sqlType(field(ArchiveFieldType.DATETIME, null, null, null)))
                .isEqualTo("timestamp");
    }

    @Test
    @DisplayName("案卷字段要求分类启用案卷管理")
    void ensureArchiveLevelAllowedShouldRejectVolumeForItemOnlyCategory() {
        assertThatThrownBy(
                        () ->
                                service.ensureArchiveLevelAllowed(
                                        category(ArchiveManagementMode.ITEM_ONLY),
                                        ArchiveLevel.VOLUME))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("该分类未启用案卷管理");
    }

    private static ArchiveCategoryDto category(ArchiveManagementMode mode) {
        return new ArchiveCategoryDto(
                1L,
                2L,
                null,
                "contract",
                "合同档案",
                mode,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now());
    }

    private static ArchiveFieldDto field(
            ArchiveFieldType type,
            Integer textLength,
            Integer decimalPrecision,
            Integer decimalScale) {
        return new ArchiveFieldDto(
                1L,
                12L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                "field_code",
                "字段",
                type,
                "f_field_code",
                textLength,
                decimalPrecision,
                decimalScale,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                false,
                false,
                true,
                0,
                null,
                null,
                null);
    }
}
```

- [ ] **Step 2: Run field definition tests and verify they fail**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionServiceTests test
```

Expected: FAIL because `ArchiveFieldDefinitionService` does not exist.

- [ ] **Step 3: Add field definition service**

Create `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionService.java`:

```java
package github.luckygc.am.module.archive.metadata.service;

import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldRequest;

@Service
class ArchiveFieldDefinitionService {

    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final int DEFAULT_TEXT_LENGTH = 500;
    private static final int DEFAULT_DECIMAL_PRECISION = 18;
    private static final int DEFAULT_DECIMAL_SCALE = 2;
    private static final Set<String> RESERVED_RECORD_FIELD_CODES =
            Set.of(
                    "id",
                    "category_code",
                    "category_name",
                    "archive_no",
                    "electronic_status",
                    "security_level_id",
                    "retention_period_id",
                    "sort_order",
                    "archived_at",
                    "archive_year",
                    "locked_flag",
                    "lock_reason",
                    "locked_by",
                    "locked_at",
                    "deleted_flag",
                    "deleted_at",
                    "deleted_by",
                    "created_by",
                    "created_at",
                    "updated_by",
                    "updated_at",
                    "fonds_code",
                    "fonds_name");

    ArchiveFieldValues validate(ArchiveFieldRequest request) {
        validateRequired(request.fieldCode(), "字段编码不能为空");
        validateRequired(request.fieldName(), "字段名称不能为空");
        String fieldCode = request.fieldCode().trim();
        if (!FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            throw badRequest("字段编码只允许小写字母、数字和下划线，并且必须以小写字母开头");
        }
        if (RESERVED_RECORD_FIELD_CODES.contains(fieldCode)) {
            throw badRequest("字段编码属于档案记录固定字段，不能作为动态字段：" + fieldCode);
        }
        String columnName = toColumnName(fieldCode);
        if (columnName.length() > POSTGRESQL_IDENTIFIER_LIMIT) {
            throw badRequest("字段编码过长，生成的动态列名超过 PostgreSQL 标识符长度限制");
        }
        ArchiveFieldType fieldType = request.fieldType();
        if (fieldType == null) {
            throw badRequest("字段类型不能为空");
        }
        Integer textLength = request.textLength();
        if (fieldType == ArchiveFieldType.TEXT && (textLength == null || textLength <= 0)) {
            textLength = DEFAULT_TEXT_LENGTH;
        }
        Integer decimalPrecision = request.decimalPrecision();
        Integer decimalScale = request.decimalScale();
        if (fieldType == ArchiveFieldType.DECIMAL) {
            decimalPrecision =
                    decimalPrecision == null ? DEFAULT_DECIMAL_PRECISION : decimalPrecision;
            decimalScale = decimalScale == null ? DEFAULT_DECIMAL_SCALE : decimalScale;
            if (decimalPrecision <= 0 || decimalScale < 0 || decimalScale >= decimalPrecision) {
                throw badRequest("小数字段精度配置不合法");
            }
        }
        ArchiveFieldControl editControl = defaultEditControl(fieldType, request.editControl());
        validateEditControl(fieldType, editControl);
        return new ArchiveFieldValues(
                normalizeArchiveLevel(request.archiveLevel()),
                normalizeFieldScope(request.fieldScope()),
                fieldCode,
                request.fieldName().trim(),
                fieldType,
                columnName,
                textLength,
                decimalPrecision,
                decimalScale,
                editControl,
                request.listVisible() == null || request.listVisible(),
                normalizeListWidth(request.listWidth()),
                request.listSortOrder() == null
                        ? layoutOrder(request.sortOrder())
                        : request.listSortOrder(),
                request.detailVisible() == null || request.detailVisible(),
                normalizeColSpan(request.detailColSpan()),
                request.detailSortOrder() == null
                        ? layoutOrder(request.sortOrder())
                        : request.detailSortOrder(),
                request.editVisible() == null || request.editVisible(),
                normalizeColSpan(request.editColSpan()),
                request.editSortOrder() == null
                        ? layoutOrder(request.sortOrder())
                        : request.editSortOrder(),
                request.exactSearchable() != null && request.exactSearchable(),
                request.dataScopeFilterable() != null && request.dataScopeFilterable(),
                request.enabled() == null || request.enabled(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    void applyValues(ArchiveField field, Long categoryId, ArchiveFieldValues values) {
        field.setCategoryId(categoryId);
        field.setArchiveLevel(values.archiveLevel());
        field.setFieldScope(values.fieldScope());
        field.setFieldCode(values.fieldCode());
        field.setFieldName(values.fieldName());
        field.setFieldType(values.fieldType());
        field.setColumnName(values.columnName());
        field.setTextLength(values.textLength());
        field.setDecimalPrecision(values.decimalPrecision());
        field.setDecimalScale(values.decimalScale());
        field.setEditControl(values.editControl());
        field.setListVisible(values.listVisible());
        field.setListWidth(values.listWidth());
        field.setListSortOrder(values.listSortOrder());
        field.setDetailVisible(values.detailVisible());
        field.setDetailColSpan(values.detailColSpan());
        field.setDetailSortOrder(values.detailSortOrder());
        field.setEditVisible(values.editVisible());
        field.setEditColSpan(values.editColSpan());
        field.setEditSortOrder(values.editSortOrder());
        field.setExactSearchable(values.exactSearchable());
        field.setDataScopeFilterable(values.dataScopeFilterable());
        field.setEnabled(values.enabled());
        field.setSortOrder(values.sortOrder());
    }

    String sqlType(ArchiveFieldDto field) {
        return switch (field.fieldType()) {
            case TEXT ->
                    "varchar("
                            + (field.textLength() == null
                                    ? DEFAULT_TEXT_LENGTH
                                    : field.textLength())
                            + ")";
            case INTEGER -> "integer";
            case DECIMAL ->
                    "numeric(%d,%d)"
                            .formatted(
                                    field.decimalPrecision() == null
                                            ? DEFAULT_DECIMAL_PRECISION
                                            : field.decimalPrecision(),
                                    field.decimalScale() == null
                                            ? DEFAULT_DECIMAL_SCALE
                                            : field.decimalScale());
            case DATE -> "date";
            case DATETIME -> "timestamp";
        };
    }

    ArchiveLevel normalizeArchiveLevel(@Nullable ArchiveLevel archiveLevel) {
        return ArchiveDynamicTableNames.normalizeArchiveLevel(archiveLevel);
    }

    ArchiveFieldScope normalizeFieldScope(@Nullable ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.normalizeFieldScope(fieldScope);
    }

    void ensureArchiveLevelAllowed(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        if (!ArchiveDynamicTableNames.supportsArchiveLevel(category, archiveLevel)) {
            throw badRequest("该分类未启用案卷管理");
        }
    }

    private String toColumnName(String fieldCode) {
        return "f_" + fieldCode;
    }

    private ArchiveFieldControl defaultEditControl(
            ArchiveFieldType fieldType, @Nullable ArchiveFieldControl editControl) {
        if (editControl != null) {
            return editControl;
        }
        return switch (fieldType) {
            case TEXT -> ArchiveFieldControl.INPUT;
            case INTEGER, DECIMAL -> ArchiveFieldControl.NUMBER;
            case DATE -> ArchiveFieldControl.DATE;
            case DATETIME -> ArchiveFieldControl.DATETIME;
        };
    }

    private void validateEditControl(ArchiveFieldType fieldType, ArchiveFieldControl editControl) {
        boolean valid =
                switch (fieldType) {
                    case TEXT ->
                            editControl == ArchiveFieldControl.INPUT
                                    || editControl == ArchiveFieldControl.TEXTAREA;
                    case INTEGER, DECIMAL -> editControl == ArchiveFieldControl.NUMBER;
                    case DATE -> editControl == ArchiveFieldControl.DATE;
                    case DATETIME -> editControl == ArchiveFieldControl.DATETIME;
                };
        if (!valid) {
            throw badRequest("编辑控件与字段类型不匹配");
        }
    }

    private @Nullable Integer normalizeListWidth(@Nullable Integer listWidth) {
        if (listWidth == null) {
            return null;
        }
        if (listWidth < 80 || listWidth > 600) {
            throw badRequest("列表列宽必须在 80 到 600 之间");
        }
        return listWidth;
    }

    private int normalizeColSpan(@Nullable Integer colSpan) {
        if (colSpan == null) {
            return 1;
        }
        if (colSpan < 1 || colSpan > 2) {
            throw badRequest("布局跨列数必须为 1 或 2");
        }
        return colSpan;
    }

    private int layoutOrder(@Nullable Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private void validateRequired(String value, String message) {
        if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    record ArchiveFieldValues(
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            String columnName,
            Integer textLength,
            Integer decimalPrecision,
            Integer decimalScale,
            ArchiveFieldControl editControl,
            boolean listVisible,
            Integer listWidth,
            int listSortOrder,
            boolean detailVisible,
            int detailColSpan,
            int detailSortOrder,
            boolean editVisible,
            int editColSpan,
            int editSortOrder,
            boolean exactSearchable,
            boolean dataScopeFilterable,
            boolean enabled,
            int sortOrder) {}
}
```

- [ ] **Step 4: Run field definition tests and verify they pass**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit field definition extraction base**

```bash
cd /home/gc/dev/archive-management
git add server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionService.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionServiceTests.java
git commit -m "refactor: 提取档案字段定义规则"
```

---

### Task 2: Wire Field Definition Service into Metadata Facade

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`

- [ ] **Step 1: Update metadata service constructor and field methods**

In `ArchiveMetadataService`, add a dependency field:

```java
private final ArchiveFieldDefinitionService fieldDefinitionService;
```

Update the constructor signature and assignment:

```java
public ArchiveMetadataService(
        ArchiveMapper archiveMapper,
        ArchiveFondsDataRepository fondsRepository,
        ArchiveClassificationSchemeDataRepository classificationSchemeRepository,
        ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository,
        ArchiveCategoryDataRepository categoryRepository,
        ArchiveFieldDataRepository fieldRepository,
        ArchiveFieldLayoutDataRepository fieldLayoutRepository,
        ArchiveSecurityLevelDataRepository securityLevelRepository,
        ArchiveRetentionPeriodDataRepository retentionPeriodRepository,
        ArchiveFieldDefinitionService fieldDefinitionService) {
    this.archiveMapper = archiveMapper;
    this.fondsRepository = fondsRepository;
    this.classificationSchemeRepository = classificationSchemeRepository;
    this.fondsCategoryScopeRepository = fondsCategoryScopeRepository;
    this.categoryRepository = categoryRepository;
    this.fieldRepository = fieldRepository;
    this.fieldLayoutRepository = fieldLayoutRepository;
    this.securityLevelRepository = securityLevelRepository;
    this.retentionPeriodRepository = retentionPeriodRepository;
    this.fieldDefinitionService = fieldDefinitionService;
}
```

Change `createField` to use the extracted service:

```java
ArchiveFieldDefinitionService.ArchiveFieldValues values =
        fieldDefinitionService.validate(request);
fieldDefinitionService.ensureArchiveLevelAllowed(category, values.archiveLevel());
ArchiveField field = new ArchiveField();
fieldDefinitionService.applyValues(field, categoryId, values);
```

Change `updateField` with the same delegation pattern:

```java
ArchiveFieldDefinitionService.ArchiveFieldValues values =
        fieldDefinitionService.validate(request);
ArchiveCategoryDto category = getCategory(categoryId);
fieldDefinitionService.ensureArchiveLevelAllowed(category, values.archiveLevel());
```

Replace remaining internal calls:

```java
ArchiveLevel normalizedLevel = fieldDefinitionService.normalizeArchiveLevel(archiveLevel);
ArchiveFieldScope normalizedScope = fieldDefinitionService.normalizeFieldScope(fieldScope);
fieldDefinitionService.ensureArchiveLevelAllowed(category, archiveLevel);
fieldDefinitionService.sqlType(field);
```

- [ ] **Step 2: Remove field-rule constants and private methods from metadata service**

Remove these members from `ArchiveMetadataService` after all call sites are delegated:

- `FIELD_CODE_PATTERN`
- `RESERVED_RECORD_FIELD_CODES`
- `DEFAULT_TEXT_LENGTH`
- `DEFAULT_DECIMAL_PRECISION`
- `DEFAULT_DECIMAL_SCALE`
- `validateFieldRequest`
- `applyFieldValues`
- `normalizeArchiveLevel`
- `normalizeFieldScope`
- `ensureArchiveLevelAllowed`
- `defaultEditControl`
- `validateEditControl`
- `normalizeListWidth`
- `normalizeColSpan`
- `toColumnName`
- `sqlType`
- `FieldValues`

Keep `IDENTIFIER_PATTERN`, `POSTGRESQL_IDENTIFIER_LIMIT`, `validateIdentifier`, `validateRequired`, `layoutOrder`, and `badRequest` until later tasks move their remaining call sites.

- [ ] **Step 3: Update metadata service test construction**

In `ArchiveMetadataServiceTests#setUp`, pass a real `ArchiveFieldDefinitionService`:

```java
service =
        new ArchiveMetadataService(
                archiveMapper,
                fondsRepository,
                classificationSchemeRepository,
                fondsCategoryScopeRepository,
                categoryRepository,
                fieldRepository,
                fieldLayoutRepository,
                securityLevelRepository,
                retentionPeriodRepository,
                new ArchiveFieldDefinitionService());
```

- [ ] **Step 4: Run metadata and field definition tests**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit field definition wiring**

```bash
cd /home/gc/dev/archive-management
git add server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java
git commit -m "refactor: 接入档案字段定义协作类"
```

---

### Task 3: Extract Dynamic Table Operations

**Files:**
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableServiceTests.java`
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`

- [ ] **Step 1: Write failing dynamic table service tests**

Create `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableServiceTests.java` with tests for table creation and column rename:

```java
package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintDto;

@DisplayName("档案动态表服务")
class ArchiveDynamicTableServiceTests {

    private final ArchiveMapper mapper = mock(ArchiveMapper.class);
    private final ArchiveDynamicTableService service =
            new ArchiveDynamicTableService(mapper, new ArchiveFieldDefinitionService());

    @Test
    @DisplayName("动态表不存在时创建 item 动态表并更新分类状态")
    void buildTableShouldCreateMissingItemTable() {
        ArchiveCategoryDto category = category();
        ArchiveFieldDto field = field("title", "f_title", ArchiveFieldType.TEXT, true);
        when(mapper.tableExists("am_archive_item_contract")).thenReturn(0);
        when(mapper.indexExists(anyString())).thenReturn(0);

        service.buildTable(
                category,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                List.of(field),
                List.of(),
                9L);

        verify(mapper).executeSql(contains("create table am_archive_item_contract"));
        verify(mapper).executeSql(contains("f_title varchar(500)"));
        verify(mapper).executeSql(contains("create index idx_am_archive_item_contract_f_title_active"));
        verify(mapper)
                .updateCategoryTableStatus(
                        eq(1L),
                        eq("item"),
                        eq("metadata"),
                        eq("am_archive_item_contract"),
                        eq("built"),
                        eq(9L));
    }

    @Test
    @DisplayName("字段编码变化时重命名动态表列")
    void syncDynamicColumnAfterFieldUpdateShouldRenameColumn() {
        ArchiveCategoryDto category = category();
        ArchiveFieldDto before = field("old_title", "f_old_title", ArchiveFieldType.TEXT, false);
        ArchiveFieldDto after = field("new_title", "f_new_title", ArchiveFieldType.TEXT, true);
        when(mapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(mapper.columnExists("am_archive_item_contract", "f_old_title")).thenReturn(1);
        when(mapper.columnExists("am_archive_item_contract", "f_new_title")).thenReturn(0);
        when(mapper.indexExists(anyString())).thenReturn(0);

        service.syncDynamicColumnAfterFieldUpdate(category, before, after);

        verify(mapper)
                .executeSql(
                        "alter table am_archive_item_contract rename column f_old_title to f_new_title");
        verify(mapper)
                .executeSql("alter table am_archive_item_contract alter column f_new_title type varchar(500)");
        verify(mapper).executeSql(contains("create index idx_am_archive_item_contract_f_new_title_active"));
    }

    @Test
    @DisplayName("唯一规则索引名使用稳定前缀")
    void uniqueConstraintIndexNameShouldUseStablePrefix() {
        assertThat(service.uniqueConstraintIndexName("contract", ArchiveLevel.ITEM, "archive_no"))
                .startsWith("uk_am_archive_constraint_");
    }

    private static ArchiveCategoryDto category() {
        return new ArchiveCategoryDto(
                1L,
                2L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private static ArchiveFieldDto field(
            String fieldCode, String columnName, ArchiveFieldType fieldType, boolean exactSearchable) {
        return new ArchiveFieldDto(
                2L,
                1L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                fieldCode,
                "标题",
                fieldType,
                columnName,
                null,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                exactSearchable,
                false,
                true,
                0,
                null,
                null,
                null);
    }
}
```

- [ ] **Step 2: Run dynamic table tests and verify they fail**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.service.ArchiveDynamicTableServiceTests test
```

Expected: FAIL because `ArchiveDynamicTableService` does not exist.

- [ ] **Step 3: Add dynamic table service**

Create `ArchiveDynamicTableService` by moving these methods from `ArchiveMetadataService`: `ensureColumn`, `dynamicTableName`, `syncDynamicColumnAfterFieldUpdate`, `isDynamicTableBuilt`, `createExactIndex`, `createUniqueIndex`, `dropIndexIfExists`, `uniqueConstraintIndexName`, and the dynamic table body of `buildTable`.

The public package-level method shape should be:

```java
@Service
class ArchiveDynamicTableService {

    private final ArchiveMapper archiveMapper;
    private final ArchiveFieldDefinitionService fieldDefinitionService;

    ArchiveDynamicTableService(
            ArchiveMapper archiveMapper, ArchiveFieldDefinitionService fieldDefinitionService) {
        this.archiveMapper = archiveMapper;
        this.fieldDefinitionService = fieldDefinitionService;
    }

    void buildTable(
            ArchiveCategoryDto category,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            List<ArchiveFieldDto> fields,
            List<ArchiveUniqueConstraintDto> constraints,
            Long userId) {
        if (fields.isEmpty()) {
            throw badRequest("该分类没有可建表字段");
        }
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        validateIdentifier(tableName, "动态表名非法");
        if (archiveMapper.tableExists(tableName) == 0) {
            String ownerTable =
                    archiveLevel == ArchiveLevel.VOLUME ? "am_archive_volume" : "am_archive_item";
            String columns =
                    fields.stream()
                            .map(field -> field.columnName() + " " + fieldDefinitionService.sqlType(field))
                            .reduce("", (left, right) -> left + ",\n    " + right);
            archiveMapper.executeSql(
                    """
                    create table %s
                    (
                        id bigint primary key references %s (id),
                        deleted_flag boolean not null default false,
                        deleted_at timestamp,
                        deleted_by bigint,
                        created_at timestamp not null default localtimestamp,
                        updated_at timestamp not null default localtimestamp%s
                    )
                    """
                            .formatted(tableName, ownerTable, columns));
        } else {
            ensureColumn(tableName, "deleted_flag", "boolean not null default false");
            ensureColumn(tableName, "deleted_at", "timestamp");
            ensureColumn(tableName, "deleted_by", "bigint");
            for (ArchiveFieldDto field : fields) {
                validateIdentifier(field.columnName(), "字段列名非法");
                if (archiveMapper.columnExists(tableName, field.columnName()) == 0) {
                    archiveMapper.executeSql(
                            "alter table %s add column %s %s"
                                    .formatted(
                                            tableName,
                                            field.columnName(),
                                            fieldDefinitionService.sqlType(field)));
                } else {
                    archiveMapper.executeSql(
                            "alter table %s alter column %s type %s"
                                    .formatted(
                                            tableName,
                                            field.columnName(),
                                            fieldDefinitionService.sqlType(field)));
                }
            }
        }
        for (ArchiveFieldDto field : fields) {
            if (field.exactSearchable()) {
                createExactIndex(tableName, field.columnName());
            }
        }
        archiveMapper.updateCategoryTableStatus(
                category.id(),
                archiveLevel.value(),
                fieldScope.value(),
                tableName,
                ArchiveTableStatus.BUILT.value(),
                userId);
        for (ArchiveUniqueConstraintDto constraint : constraints) {
            if (fieldScope == ArchiveFieldScope.METADATA
                    && constraint.enabled()
                    && constraint.archiveLevel() == archiveLevel) {
                createUniqueIndex(tableName, constraint);
            }
        }
    }
}
```

Use the exact private method bodies from `ArchiveMetadataService` for identifier validation, index creation and `stableIdentifier` naming. Keep `validateIdentifier` local to this class so dynamic SQL safety travels with dynamic SQL construction.

- [ ] **Step 4: Wire dynamic table service into metadata service**

Add constructor dependency:

```java
private final ArchiveDynamicTableService dynamicTableService;
```

In `buildTable`, replace the DDL body with:

```java
dynamicTableService.buildTable(
        category,
        archiveLevel,
        fieldScope,
        fields,
        listUniqueConstraints(categoryId),
        userId);
return getCategory(categoryId);
```

In `updateField`, replace:

```java
syncDynamicColumnAfterFieldUpdate(category, current, updatedField);
```

with:

```java
dynamicTableService.syncDynamicColumnAfterFieldUpdate(category, current, updatedField);
```

Replace remaining dynamic table checks with:

```java
dynamicTableService.isDynamicTableBuilt(category, archiveLevel, fieldScope)
dynamicTableService.dynamicTableName(category, archiveLevel, fieldScope)
```

- [ ] **Step 5: Update metadata service tests for constructor dependency**

In `ArchiveMetadataServiceTests#setUp`, instantiate:

```java
ArchiveFieldDefinitionService fieldDefinitionService = new ArchiveFieldDefinitionService();
ArchiveDynamicTableService dynamicTableService =
        new ArchiveDynamicTableService(archiveMapper, fieldDefinitionService);
```

Then pass both dependencies into `ArchiveMetadataService`.

- [ ] **Step 6: Run metadata and dynamic table tests**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveDynamicTableServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit dynamic table extraction**

```bash
cd /home/gc/dev/archive-management
git add server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableService.java \
    server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableServiceTests.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java
git commit -m "refactor: 提取档案动态表协作类"
```

---

### Task 4: Extract Field Layout Operations

**Files:**
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutServiceTests.java`
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`

- [ ] **Step 1: Write failing field layout service tests**

Create `ArchiveFieldLayoutServiceTests` with default layout and duplicate layout field validation:

```java
package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldLayoutDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutItemRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutRequest;

@DisplayName("档案字段布局服务")
class ArchiveFieldLayoutServiceTests {

    private final ArchiveFieldLayoutDataRepository repository =
            mock(ArchiveFieldLayoutDataRepository.class);
    private final ArchiveFieldLayoutService service =
            new ArchiveFieldLayoutService(repository, new ArchiveFieldDefinitionService());

    @Test
    @DisplayName("无保存布局时生成默认表格布局")
    void publicLayoutItemsShouldReturnDefaultItems() {
        List<ArchiveMetadataService.ArchiveFieldLayoutItemDto> items =
                service.publicLayoutItems(1L, ArchiveLayoutSurface.TABLE, List.of(field(2L)));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().fieldId()).isEqualTo(2L);
        assertThat(items.getFirst().visible()).isTrue();
        assertThat(items.getFirst().rowOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("保存布局时拒绝重复字段")
    void savePublicLayoutShouldRejectDuplicateField() {
        ArchiveFieldDto field = field(2L);

        assertThatThrownBy(
                        () ->
                                service.savePublicLayout(
                                        1L,
                                        ArchiveLayoutSurface.TABLE,
                                        List.of(field),
                                        new ArchiveFieldLayoutRequest(
                                                List.of(
                                                        new ArchiveFieldLayoutItemRequest(
                                                                2L, true, 120, 1, 0, 0),
                                                        new ArchiveFieldLayoutItemRequest(
                                                                2L, true, 130, 1, 1, 0))),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("布局字段不能重复");
    }

    private static ArchiveFieldDto field(Long id) {
        return new ArchiveFieldDto(
                id,
                1L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                "title",
                "标题",
                ArchiveFieldType.TEXT,
                "f_title",
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                160,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                false,
                false,
                true,
                0,
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
```

- [ ] **Step 2: Run layout tests and verify they fail**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.service.ArchiveFieldLayoutServiceTests test
```

Expected: FAIL because `ArchiveFieldLayoutService` does not exist.

- [ ] **Step 3: Add field layout service**

Create `ArchiveFieldLayoutService` by moving layout methods from `ArchiveMetadataService`.

Use this exact mapping:

- `applyEffectiveLayout` moves unchanged except imports for `Comparator`, `Collectors`, `Map` and `List`.
- `publicLayoutItems` moves unchanged and remains package-visible for tests.
- `layoutItems` moves unchanged and uses `fieldLayoutRepository`.
- `defaultLayoutItems` moves unchanged.
- `saveFieldLayout` is renamed to `savePublicLayout`; it receives `Long categoryId`, `ArchiveLayoutSurface surface`, `List<ArchiveFieldDto> enabledFields`, `ArchiveFieldLayoutRequest request`, and `Long userId`. It no longer loads category or fields; the facade supplies `enabledFields`.
- `applyLayout`, `copyField`, `surfaceVisible`, `surfaceColSpan`, and `layoutOrder(ArchiveLayoutSurface, ArchiveFieldDto)` move unchanged.

Make `ArchiveFieldDefinitionService.normalizeListWidth` and `normalizeColSpan` package-visible so `savePublicLayout` can reuse the same validation rules. The moved `savePublicLayout` must call `fieldDefinitionService.normalizeListWidth` and `fieldDefinitionService.normalizeColSpan`.

- [ ] **Step 4: Wire layout service into metadata service**

Add constructor dependency:

```java
private final ArchiveFieldLayoutService fieldLayoutService;
```

Replace layout methods in `ArchiveMetadataService`:

```java
return new ArchiveFieldLayoutDto(
        surface,
        "public",
        fieldLayoutService.publicLayoutItems(categoryId, surface, fields));
```

Replace the `saveFieldLayout` call:

```java
fieldLayoutService.savePublicLayout(
        categoryId,
        surface,
        listEnabledFields(categoryId, normalizedLevel, normalizedScope),
        request,
        userId);
```

Replace effective field layout:

```java
return fieldLayoutService.applyEffectiveLayout(categoryId, surface, fields);
```

- [ ] **Step 5: Update constructor in metadata service tests**

In `ArchiveMetadataServiceTests#setUp`, instantiate and pass:

```java
ArchiveFieldLayoutService fieldLayoutService =
        new ArchiveFieldLayoutService(fieldLayoutRepository, fieldDefinitionService);
```

- [ ] **Step 6: Run metadata and layout tests**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldLayoutServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit field layout extraction**

```bash
cd /home/gc/dev/archive-management
git add server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutService.java \
    server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionService.java \
    server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutServiceTests.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java
git commit -m "refactor: 提取档案字段布局协作类"
```

---

### Task 5: Extract Unique Constraint Operations

**Files:**
- Create: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintServiceTests.java`
- Create: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`

- [ ] **Step 1: Write failing unique constraint service tests**

Create `ArchiveUniqueConstraintServiceTests`:

```java
package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintRequest;

@DisplayName("档案唯一规则服务")
class ArchiveUniqueConstraintServiceTests {

    private final ArchiveMapper mapper = mock(ArchiveMapper.class);
    private final ArchiveFieldDefinitionService fieldDefinitionService =
            new ArchiveFieldDefinitionService();
    private final ArchiveDynamicTableService dynamicTableService =
            new ArchiveDynamicTableService(mapper, fieldDefinitionService);
    private final ArchiveUniqueConstraintService service =
            new ArchiveUniqueConstraintService(mapper, fieldDefinitionService, dynamicTableService);

    @Test
    @DisplayName("唯一规则字段不能为空")
    void validateShouldRejectEmptyFields() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        category(),
                                        List.of(field(1L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA)),
                                        new ArchiveUniqueConstraintRequest(
                                                ArchiveLevel.ITEM,
                                                "archive_no_unique",
                                                "档号唯一",
                                                true,
                                                List.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("唯一约束字段不能为空");
    }

    @Test
    @DisplayName("唯一规则字段必须属于同一层级和元数据范围")
    void validateShouldRejectWrongFieldLevelOrScope() {
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        category(),
                                        List.of(field(1L, ArchiveLevel.VOLUME, ArchiveFieldScope.METADATA)),
                                        new ArchiveUniqueConstraintRequest(
                                                ArchiveLevel.ITEM,
                                                "archive_no_unique",
                                                "档号唯一",
                                                true,
                                                List.of(1L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("唯一约束字段必须和约束层级一致");
    }

    @Test
    @DisplayName("标记唯一规则字段为可精确检索")
    void markFieldsSearchableShouldDelegateToMapper() {
        service.markUniqueConstraintFieldsSearchable(
                category(),
                List.of(field(1L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA)),
                List.of(1L),
                9L);

        verify(mapper).markFieldsExactSearchable(eq(1L), anyList(), eq(9L));
    }

    private static ArchiveCategoryDto category() {
        return new ArchiveCategoryDto(
                1L,
                2L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private static ArchiveFieldDto field(Long id, ArchiveLevel level, ArchiveFieldScope scope) {
        return new ArchiveFieldDto(
                id,
                1L,
                level,
                scope,
                "archive_title",
                "题名",
                ArchiveFieldType.TEXT,
                "f_archive_title",
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                false,
                false,
                true,
                0,
                null,
                null,
                null);
    }
}
```

- [ ] **Step 2: Run unique constraint tests and verify they fail**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.service.ArchiveUniqueConstraintServiceTests test
```

Expected: FAIL because `ArchiveUniqueConstraintService` does not exist.

- [ ] **Step 3: Add unique constraint service**

Create `ArchiveUniqueConstraintService` by moving unique rule methods from `ArchiveMetadataService`.

Use this exact mapping:

- `listUniqueConstraintFields` moves unchanged and remains package-visible for tests.
- `replaceUniqueConstraintFields` moves unchanged and remains package-visible for facade delegation.
- `markUniqueConstraintFieldsSearchable` moves with an added `List<ArchiveFieldDto> fields` parameter; replace the old `listFields(category.id())` call with that parameter.
- `validateUniqueConstraintRequest` is renamed to `validate`; it receives `ArchiveCategoryDto category`, `List<ArchiveFieldDto> fields`, and `ArchiveUniqueConstraintRequest request`. Replace the old `listFields(category.id())` call with the `fields` parameter.
- `mapUniqueConstraint` and `mapUniqueConstraintField` move unchanged.
- `UniqueConstraintValues` moves from `ArchiveMetadataService` into `ArchiveUniqueConstraintService` as `ArchiveUniqueConstraintValues` with the same fields.

The service constructor receives `ArchiveMapper`, `ArchiveFieldDefinitionService`, and `ArchiveDynamicTableService`. It uses `fieldDefinitionService.normalizeArchiveLevel` and `fieldDefinitionService.ensureArchiveLevelAllowed` during validation, and uses `dynamicTableService.createExactIndex`, `dynamicTableService.dynamicTableName`, `dynamicTableService.isDynamicTableBuilt`, `dynamicTableService.createUniqueIndex`, `dynamicTableService.dropIndexIfExists`, and `dynamicTableService.uniqueConstraintIndexName` for index coordination.

- [ ] **Step 4: Wire unique constraint service into metadata service**

Add constructor dependency:

```java
private final ArchiveUniqueConstraintService uniqueConstraintService;
```

Replace public methods with facade delegation while preserving signatures:

```java
public List<ArchiveUniqueConstraintDto> listUniqueConstraints(Long categoryId) {
    requireId(categoryId);
    return uniqueConstraintService.list(categoryId);
}
```

For create/update/delete, keep `requireId`, `getCategory`, `getUniqueConstraint`, and category ownership checks in `ArchiveMetadataService`, then delegate validation, relation replacement, searchable marking, and index sync to `ArchiveUniqueConstraintService`. This keeps transaction boundaries and public error behavior in the facade.

- [ ] **Step 5: Update metadata service test setup**

Instantiate:

```java
ArchiveUniqueConstraintService uniqueConstraintService =
        new ArchiveUniqueConstraintService(
                archiveMapper, fieldDefinitionService, dynamicTableService);
```

Pass it into `ArchiveMetadataService`.

- [ ] **Step 6: Run metadata and unique constraint tests**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveUniqueConstraintServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveDynamicTableServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit unique constraint extraction**

```bash
cd /home/gc/dev/archive-management
git add server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintService.java \
    server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintServiceTests.java \
    server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java
git commit -m "refactor: 提取档案唯一规则协作类"
```

---

### Task 6: Cleanup Metadata Facade and Verify Architecture

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldDefinitionService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveUniqueConstraintService.java`

- [ ] **Step 1: Remove dead imports and private helpers**

In `ArchiveMetadataService`, remove imports that are no longer used after extraction:

```java
import java.util.regex.Pattern;
import org.springframework.jdbc.support.JdbcUtils;
```

Only remove `JdbcUtils` if all row mapping remains outside `ArchiveMetadataService`. Keep imports that still support fonds/category mapping.

Remove private methods that now have no call sites:

- `ensureColumn`
- `dynamicTableName`
- `syncDynamicColumnAfterFieldUpdate`
- `createExactIndex`
- `createUniqueIndex`
- `dropIndexIfExists`
- `uniqueConstraintIndexName`
- `validateUniqueConstraintRequest`
- `listUniqueConstraintFields`
- `replaceUniqueConstraintFields`
- `markUniqueConstraintFieldsSearchable`
- `applyEffectiveLayout`
- `publicLayoutItems`
- `layoutItems`
- `defaultLayoutItems`
- `saveFieldLayout`
- `applyLayout`
- `copyField`
- `surfaceVisible`
- `surfaceColSpan`

- [ ] **Step 2: Run Spotless apply for Java formatting**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q spotless:apply
```

Expected: command exits 0 and formats touched Java files.

- [ ] **Step 3: Run focused metadata test suite**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveDynamicTableServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldLayoutServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveUniqueConstraintServiceTests test
```

Expected: PASS.

- [ ] **Step 4: Run compile and architecture checks**

Run:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -DskipTests test-compile
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.architecture.ArchitectureRulesTest test
```

Expected: both commands PASS.

- [ ] **Step 5: Run repository diff checks**

Run:

```bash
cd /home/gc/dev/archive-management && git diff --check
cd /home/gc/dev/archive-management && git status --short
```

Expected: `git diff --check` exits 0. `git status --short` shows only the intended Java and test files before commit.

- [ ] **Step 6: Commit cleanup**

```bash
cd /home/gc/dev/archive-management
git add server/src/main/java/github/luckygc/am/module/archive/metadata/service \
    server/src/test/java/github/luckygc/am/module/archive/metadata/service \
    server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java
git commit -m "refactor: 收口档案元数据服务门面"
```

---

## Final Verification

After all tasks:

```bash
cd /home/gc/dev/archive-management/server && mvn -q -DskipTests test-compile
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.architecture.ArchitectureRulesTest test
cd /home/gc/dev/archive-management && make server-format-check
cd /home/gc/dev/archive-management && git diff --check
```

Expected: all commands pass.
