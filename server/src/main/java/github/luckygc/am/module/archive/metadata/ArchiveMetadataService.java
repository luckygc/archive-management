package github.luckygc.am.module.archive.metadata;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@Service
public class ArchiveMetadataService {

    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final Set<String> RESERVED_RECORD_FIELD_CODES =
            Set.of(
                    "id",
                    "category_code",
                    "category_name",
                    "archive_no",
                    "electronic_status",
                    "security_level",
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
    private static final int DEFAULT_TEXT_LENGTH = 500;
    private static final int DEFAULT_DECIMAL_PRECISION = 18;
    private static final int DEFAULT_DECIMAL_SCALE = 2;

    private final ArchiveMapper archiveMapper;
    private final ArchiveFondsDataRepository fondsRepository;
    private final ArchiveCategoryDataRepository categoryRepository;
    private final ArchiveFieldDataRepository fieldRepository;
    private final ArchiveFieldLayoutDataRepository fieldLayoutRepository;

    public ArchiveMetadataService(
            ArchiveMapper archiveMapper,
            ArchiveFondsDataRepository fondsRepository,
            ArchiveCategoryDataRepository categoryRepository,
            ArchiveFieldDataRepository fieldRepository,
            ArchiveFieldLayoutDataRepository fieldLayoutRepository) {
        this.archiveMapper = archiveMapper;
        this.fondsRepository = fondsRepository;
        this.categoryRepository = categoryRepository;
        this.fieldRepository = fieldRepository;
        this.fieldLayoutRepository = fieldLayoutRepository;
    }

    public List<ArchiveFondsDto> listFonds(@Nullable Boolean enabled) {
        List<ArchiveFonds> fonds =
                enabled == null ? fondsRepository.list() : fondsRepository.list(enabled);
        return fonds.stream().map(this::mapFonds).toList();
    }

    @Transactional
    public ArchiveFondsDto createFonds(ArchiveFondsRequest request, Long userId) {
        validateRequired(request.fondsCode(), "全宗编码不能为空");
        validateRequired(request.fondsName(), "全宗名称不能为空");
        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setFondsCode(request.fondsCode().trim());
        fonds.setFondsName(request.fondsName().trim());
        fonds.setEnabled(request.enabled() == null || request.enabled());
        fonds.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        fonds.setCreatedBy(userId);
        fonds.setUpdatedBy(userId);
        return mapFonds(fondsRepository.insert(fonds));
    }

    @Transactional
    public ArchiveFondsDto updateFonds(Long id, ArchiveFondsRequest request, Long userId) {
        requireId(id);
        validateRequired(request.fondsCode(), "全宗编码不能为空");
        validateRequired(request.fondsName(), "全宗名称不能为空");
        ArchiveFonds fonds = fondsRepository.findById(id).orElseThrow(() -> notFound("全宗不存在"));
        fonds.setFondsCode(request.fondsCode().trim());
        fonds.setFondsName(request.fondsName().trim());
        fonds.setEnabled(request.enabled() == null || request.enabled());
        fonds.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        fonds.setUpdatedBy(userId);
        return mapFonds(fondsRepository.update(fonds));
    }

    @Transactional
    public void deleteFonds(Long id, Long userId) {
        requireId(id);
        ArchiveFonds fonds = fondsRepository.findById(id).orElseThrow(() -> notFound("全宗不存在"));
        fonds.setUpdatedBy(userId);
        fondsRepository.update(fonds);
        fondsRepository.delete(fonds);
    }

    public ArchiveFondsDto getFonds(Long id) {
        requireId(id);
        return fondsRepository
                .findById(id)
                .map(this::mapFonds)
                .orElseThrow(() -> notFound("全宗不存在"));
    }

    public ArchiveFondsDto getFondsByCode(String fondsCode) {
        validateRequired(fondsCode, "全宗编码不能为空");
        return fondsRepository
                .find(fondsCode.trim())
                .map(this::mapFonds)
                .orElseThrow(() -> notFound("全宗不存在"));
    }

    public List<ArchiveCategoryDto> listCategories(@Nullable Boolean enabled) {
        List<ArchiveCategory> categories =
                enabled == null ? categoryRepository.list() : categoryRepository.list(enabled);
        return categories.stream().map(this::mapCategory).toList();
    }

    @Transactional
    public ArchiveCategoryDto createCategory(ArchiveCategoryRequest request, Long userId) {
        validateRequired(request.categoryCode(), "分类编码不能为空");
        validateRequired(request.categoryName(), "分类名称不能为空");
        validateParentCategory(null, request.parentId());
        ArchiveCategory category = new ArchiveCategory();
        category.setParentId(request.parentId());
        category.setCategoryCode(request.categoryCode().trim());
        category.setCategoryName(request.categoryName().trim());
        category.setManagementMode(normalizeManagementMode(request.managementMode()));
        category.setEnabled(request.enabled() == null || request.enabled());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setCreatedBy(userId);
        category.setUpdatedBy(userId);
        return mapCategory(categoryRepository.insert(category));
    }

    @Transactional
    public ArchiveCategoryDto updateCategory(Long id, ArchiveCategoryRequest request, Long userId) {
        requireId(id);
        validateRequired(request.categoryCode(), "分类编码不能为空");
        validateRequired(request.categoryName(), "分类名称不能为空");
        validateParentCategory(id, request.parentId());
        ArchiveCategory category =
                categoryRepository.findById(id).orElseThrow(() -> notFound("档案分类不存在"));
        category.setParentId(request.parentId());
        category.setCategoryCode(request.categoryCode().trim());
        category.setCategoryName(request.categoryName().trim());
        category.setManagementMode(normalizeManagementMode(request.managementMode()));
        category.setEnabled(request.enabled() == null || request.enabled());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setUpdatedBy(userId);
        return mapCategory(categoryRepository.update(category));
    }

    @Transactional
    public void deleteCategory(Long id, Long userId) {
        requireId(id);
        if (archiveMapper.countChildCategories(id) > 0) {
            throw badRequest("存在子分类，不能删除");
        }
        ArchiveCategory category =
                categoryRepository.findById(id).orElseThrow(() -> notFound("档案分类不存在"));
        category.setUpdatedBy(userId);
        categoryRepository.update(category);
        categoryRepository.delete(category);
    }

    public ArchiveCategoryDto getCategory(Long id) {
        requireId(id);
        return categoryRepository
                .findById(id)
                .map(this::mapCategory)
                .orElseThrow(() -> notFound("档案分类不存在"));
    }

    public List<ArchiveFieldDto> listFields(Long categoryId) {
        requireId(categoryId);
        return fieldRepository.list(categoryId).stream().map(this::mapField).toList();
    }

    public List<ArchiveFieldDto> listEnabledFields(Long categoryId) {
        return listEnabledFields(categoryId, ArchiveLevel.ITEM);
    }

    public List<ArchiveFieldDto> listEnabledFields(Long categoryId, ArchiveLevel archiveLevel) {
        return listEnabledFields(categoryId, archiveLevel, ArchiveFieldScope.METADATA);
    }

    public List<ArchiveFieldDto> listEnabledFields(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        requireId(categoryId);
        return fieldRepository
                .list(
                        categoryId,
                        normalizeArchiveLevel(archiveLevel),
                        normalizeFieldScope(fieldScope),
                        true)
                .stream()
                .map(this::mapField)
                .toList();
    }

    public List<ArchiveFieldDto> listEffectiveFields(
            Long categoryId, ArchiveLayoutSurface surface, Long userId) {
        return listEffectiveFields(categoryId, ArchiveLevel.ITEM, surface, userId);
    }

    public List<ArchiveFieldDto> listEffectiveFields(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveLayoutSurface surface, Long userId) {
        return listEffectiveFields(
                categoryId, archiveLevel, ArchiveFieldScope.METADATA, surface, userId);
    }

    public List<ArchiveFieldDto> listEffectiveFields(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            Long userId) {
        List<ArchiveFieldDto> fields = listEnabledFields(categoryId, archiveLevel, fieldScope);
        return applyEffectiveLayout(categoryId, surface, fields);
    }

    public ArchiveFieldLayoutDto getFieldLayout(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveLayoutSurface surface) {
        return getFieldLayout(categoryId, archiveLevel, ArchiveFieldScope.METADATA, surface);
    }

    public ArchiveFieldLayoutDto getFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface) {
        requireId(categoryId);
        getCategory(categoryId);
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        ArchiveFieldScope normalizedScope = normalizeFieldScope(fieldScope);
        List<ArchiveFieldDto> fields =
                listEnabledFields(categoryId, normalizedLevel, normalizedScope);
        return new ArchiveFieldLayoutDto(
                surface, "public", publicLayoutItems(categoryId, surface, fields));
    }

    @Transactional
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            Long categoryId, ArchiveLayoutSurface surface, ArchiveFieldLayoutRequest request) {
        return savePublicFieldLayout(categoryId, ArchiveLevel.ITEM, surface, request, null);
    }

    @Transactional
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveLayoutSurface surface,
            ArchiveFieldLayoutRequest request,
            Long userId) {
        return savePublicFieldLayout(
                categoryId, archiveLevel, ArchiveFieldScope.METADATA, surface, request, userId);
    }

    @Transactional
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            @Nullable ArchiveFieldLayoutRequest request,
            @Nullable Long userId) {
        saveFieldLayout(categoryId, archiveLevel, fieldScope, surface, request, userId);
        return getFieldLayout(categoryId, archiveLevel, fieldScope, surface);
    }

    @Transactional
    public ArchiveFieldDto createField(Long categoryId, ArchiveFieldRequest request, Long userId) {
        requireId(categoryId);
        ArchiveCategoryDto category = getCategory(categoryId);
        FieldValues values = validateFieldRequest(request);
        ensureArchiveLevelAllowed(category, values.archiveLevel());
        ArchiveField field = new ArchiveField();
        applyFieldValues(field, categoryId, values);
        field.setCreatedBy(userId);
        field.setUpdatedBy(userId);
        return mapField(fieldRepository.insert(field));
    }

    @Transactional
    public ArchiveFieldDto updateField(
            Long categoryId, Long fieldId, ArchiveFieldRequest request, Long userId) {
        requireId(categoryId);
        requireId(fieldId);
        FieldValues values = validateFieldRequest(request);
        ArchiveCategoryDto category = getCategory(categoryId);
        ensureArchiveLevelAllowed(category, values.archiveLevel());
        ArchiveFieldDto current = getField(fieldId);
        if (!current.categoryId().equals(categoryId)) {
            throw notFound("字段定义不存在");
        }
        if (!current.fieldType().equals(values.fieldType())) {
            throw badRequest("已建字段不允许修改字段类型");
        }
        if (current.archiveLevel() != values.archiveLevel()
                && isDynamicTableBuilt(category, current.archiveLevel())) {
            throw badRequest("已建字段不允许修改适用层级");
        }
        ArchiveField field =
                fieldRepository.findById(fieldId).orElseThrow(() -> notFound("字段定义不存在"));
        applyFieldValues(field, categoryId, values);
        field.setUpdatedBy(userId);
        ArchiveFieldDto updatedField = mapField(fieldRepository.update(field));
        syncDynamicColumnAfterFieldUpdate(category, current, updatedField);
        return updatedField;
    }

    @Transactional
    public void deleteField(Long categoryId, Long fieldId, Long userId) {
        requireId(categoryId);
        requireId(fieldId);
        ArchiveField field =
                fieldRepository.findById(fieldId).orElseThrow(() -> notFound("字段定义不存在"));
        if (!field.getCategoryId().equals(categoryId)) {
            throw notFound("字段定义不存在");
        }
        field.setUpdatedBy(userId);
        fieldRepository.update(field);
        fieldRepository.delete(field);
    }

    public ArchiveFieldDto getField(Long id) {
        requireId(id);
        return fieldRepository
                .findById(id)
                .map(this::mapField)
                .orElseThrow(() -> notFound("字段定义不存在"));
    }

    @Transactional
    public ArchiveCategoryDto buildTable(Long categoryId) {
        return buildTable(categoryId, ArchiveLevel.ITEM, null);
    }

    @Transactional
    public ArchiveCategoryDto buildTable(Long categoryId, ArchiveLevel requestedLevel) {
        return buildTable(categoryId, requestedLevel, null);
    }

    @Transactional
    public ArchiveCategoryDto buildTable(
            Long categoryId, ArchiveLevel requestedLevel, Long userId) {
        return buildTable(categoryId, requestedLevel, ArchiveFieldScope.METADATA, userId);
    }

    @Transactional
    public ArchiveCategoryDto buildTable(
            Long categoryId,
            ArchiveLevel requestedLevel,
            ArchiveFieldScope requestedScope,
            Long userId) {
        ArchiveCategoryDto category = getCategory(categoryId);
        ArchiveLevel archiveLevel = normalizeArchiveLevel(requestedLevel);
        ArchiveFieldScope fieldScope = normalizeFieldScope(requestedScope);
        ensureArchiveLevelAllowed(category, archiveLevel);
        List<ArchiveFieldDto> fields = listEnabledFields(categoryId, archiveLevel, fieldScope);
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
                            .map(field -> field.columnName() + " " + sqlType(field))
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
                                    .formatted(tableName, field.columnName(), sqlType(field)));
                } else {
                    archiveMapper.executeSql(
                            "alter table %s alter column %s type %s"
                                    .formatted(tableName, field.columnName(), sqlType(field)));
                }
            }
        }
        for (ArchiveFieldDto field : fields) {
            if (field.exactSearchable()) {
                createExactIndex(tableName, field.columnName());
            }
        }
        archiveMapper.updateCategoryTableStatus(
                categoryId,
                archiveLevel.value(),
                fieldScope.value(),
                tableName,
                ArchiveTableStatus.BUILT.value(),
                userId);
        for (ArchiveUniqueConstraintDto constraint : listUniqueConstraints(categoryId)) {
            if (fieldScope == ArchiveFieldScope.METADATA
                    && constraint.enabled()
                    && constraint.archiveLevel() == archiveLevel) {
                createUniqueIndex(categoryId, tableName, constraint);
            }
        }
        return getCategory(categoryId);
    }

    public List<ArchiveUniqueConstraintDto> listUniqueConstraints(Long categoryId) {
        requireId(categoryId);
        return archiveMapper.listUniqueConstraints(categoryId).stream()
                .map(
                        row ->
                                mapUniqueConstraint(
                                        row,
                                        listUniqueConstraintFields(number(row, "id").longValue())))
                .toList();
    }

    @Transactional
    public ArchiveUniqueConstraintDto createUniqueConstraint(
            Long categoryId, ArchiveUniqueConstraintRequest request, Long userId) {
        requireId(categoryId);
        ArchiveCategoryDto category = getCategory(categoryId);
        UniqueConstraintValues values = validateUniqueConstraintRequest(category, request);
        String indexName =
                uniqueConstraintIndexName(
                        category.categoryCode(), values.archiveLevel(), values.constraintCode());
        Long id =
                archiveMapper.insertUniqueConstraint(
                        categoryId,
                        values.archiveLevel().value(),
                        values.constraintCode(),
                        values.constraintName(),
                        indexName,
                        values.enabled(),
                        userId);
        replaceUniqueConstraintFields(id, values.fieldIds());
        markUniqueConstraintFieldsSearchable(category, values.fieldIds(), userId);
        ArchiveUniqueConstraintDto constraint = getUniqueConstraint(id);
        if (constraint.enabled() && isDynamicTableBuilt(category, constraint.archiveLevel())) {
            createUniqueIndex(
                    categoryId, dynamicTableName(category, constraint.archiveLevel()), constraint);
        }
        return constraint;
    }

    @Transactional
    public ArchiveUniqueConstraintDto updateUniqueConstraint(
            Long categoryId,
            Long constraintId,
            ArchiveUniqueConstraintRequest request,
            Long userId) {
        requireId(categoryId);
        requireId(constraintId);
        ArchiveCategoryDto category = getCategory(categoryId);
        ArchiveUniqueConstraintDto current = getUniqueConstraint(constraintId);
        if (!current.categoryId().equals(categoryId)) {
            throw notFound("唯一约束不存在");
        }
        dropIndexIfExists(current.indexName());
        UniqueConstraintValues values = validateUniqueConstraintRequest(category, request);
        String indexName =
                uniqueConstraintIndexName(
                        category.categoryCode(), values.archiveLevel(), values.constraintCode());
        int updated =
                archiveMapper.updateUniqueConstraint(
                        constraintId,
                        categoryId,
                        values.archiveLevel().value(),
                        values.constraintCode(),
                        values.constraintName(),
                        indexName,
                        values.enabled(),
                        userId);
        if (updated == 0) {
            throw notFound("唯一约束不存在");
        }
        replaceUniqueConstraintFields(constraintId, values.fieldIds());
        markUniqueConstraintFieldsSearchable(category, values.fieldIds(), userId);
        ArchiveUniqueConstraintDto constraint = getUniqueConstraint(constraintId);
        if (constraint.enabled() && isDynamicTableBuilt(category, constraint.archiveLevel())) {
            createUniqueIndex(
                    categoryId, dynamicTableName(category, constraint.archiveLevel()), constraint);
        }
        return constraint;
    }

    @Transactional
    public void deleteUniqueConstraint(Long categoryId, Long constraintId, Long userId) {
        requireId(categoryId);
        requireId(constraintId);
        ArchiveUniqueConstraintDto constraint = getUniqueConstraint(constraintId);
        if (!constraint.categoryId().equals(categoryId)) {
            throw notFound("唯一约束不存在");
        }
        dropIndexIfExists(constraint.indexName());
        int updated = archiveMapper.deleteUniqueConstraint(constraintId, categoryId, userId);
        if (updated == 0) {
            throw notFound("唯一约束不存在");
        }
    }

    public ArchiveUniqueConstraintDto getUniqueConstraint(Long id) {
        requireId(id);
        Map<String, @Nullable Object> row = archiveMapper.getUniqueConstraint(id);
        if (row == null) {
            throw notFound("唯一约束不存在");
        }
        return mapUniqueConstraint(row, listUniqueConstraintFields(id));
    }

    private List<ArchiveUniqueConstraintFieldDto> listUniqueConstraintFields(Long constraintId) {
        return archiveMapper.listUniqueConstraintFields(constraintId).stream()
                .map(this::mapUniqueConstraintField)
                .toList();
    }

    private void replaceUniqueConstraintFields(Long constraintId, List<Long> fieldIds) {
        archiveMapper.deleteUniqueConstraintFields(constraintId);
        for (int index = 0; index < fieldIds.size(); index++) {
            archiveMapper.insertUniqueConstraintField(constraintId, fieldIds.get(index), index + 1);
        }
    }

    private void markUniqueConstraintFieldsSearchable(
            ArchiveCategoryDto category, List<Long> fieldIds, Long userId) {
        if (fieldIds.isEmpty()) {
            return;
        }
        archiveMapper.markFieldsExactSearchable(category.id(), fieldIds, userId);
        listFields(category.id()).stream()
                .filter(field -> fieldIds.contains(field.id()))
                .filter(
                        field ->
                                isDynamicTableBuilt(
                                        category, field.archiveLevel(), field.fieldScope()))
                .forEach(
                        field ->
                                createExactIndex(
                                        dynamicTableName(
                                                category, field.archiveLevel(), field.fieldScope()),
                                        field.columnName()));
    }

    private void ensureColumn(String tableName, String columnName, String type) {
        validateIdentifier(tableName, "动态表名非法");
        validateIdentifier(columnName, "字段列名非法");
        if (archiveMapper.columnExists(tableName, columnName) == 0) {
            archiveMapper.executeSql(
                    "alter table %s add column %s %s".formatted(tableName, columnName, type));
        }
    }

    private String dynamicTableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return dynamicTableName(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private String dynamicTableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.tableName(category, archiveLevel, fieldScope);
    }

    private void syncDynamicColumnAfterFieldUpdate(
            ArchiveCategoryDto category, ArchiveFieldDto before, ArchiveFieldDto after) {
        if (before.archiveLevel() != after.archiveLevel()) {
            throw badRequest("已建字段不允许修改适用层级");
        }
        if (!isDynamicTableBuilt(category, after.archiveLevel(), after.fieldScope())) {
            return;
        }
        String tableName = dynamicTableName(category, after.archiveLevel(), after.fieldScope());
        validateIdentifier(tableName, "动态表名非法");
        validateIdentifier(before.columnName(), "字段列名非法");
        validateIdentifier(after.columnName(), "字段列名非法");
        if (archiveMapper.tableExists(tableName) == 0) {
            return;
        }
        if (archiveMapper.columnExists(tableName, before.columnName()) == 0) {
            ensureColumn(tableName, after.columnName(), sqlType(after));
            return;
        }
        if (!before.columnName().equals(after.columnName())) {
            if (archiveMapper.columnExists(tableName, after.columnName()) > 0) {
                throw badRequest("动态表已存在同名字段列，不能修改字段编码");
            }
            archiveMapper.executeSql(
                    "alter table %s rename column %s to %s"
                            .formatted(tableName, before.columnName(), after.columnName()));
        }
        archiveMapper.executeSql(
                "alter table %s alter column %s type %s"
                        .formatted(tableName, after.columnName(), sqlType(after)));
        if (after.exactSearchable()) {
            createExactIndex(tableName, after.columnName());
        }
    }

    private void createExactIndex(String tableName, String columnName) {
        validateIdentifier(tableName, "动态表名非法");
        validateIdentifier(columnName, "字段列名非法");
        String indexName = "idx_" + tableName + "_" + columnName + "_active";
        if (indexName.length() > 63) {
            indexName = "idx_archive_exact_" + Math.abs((tableName + "_" + columnName).hashCode());
        }
        validateIdentifier(indexName, "索引名非法");
        if (archiveMapper.indexExists(indexName) == 0) {
            archiveMapper.executeSql(
                    "create index %s on %s (%s) where deleted_flag = false"
                            .formatted(indexName, tableName, columnName));
        }
    }

    private void createUniqueIndex(
            Long categoryId, String tableName, ArchiveUniqueConstraintDto constraint) {
        validateIdentifier(tableName, "动态表名非法");
        validateIdentifier(constraint.indexName(), "索引名非法");
        List<String> columns =
                constraint.fields().stream()
                        .map(ArchiveUniqueConstraintFieldDto::columnName)
                        .toList();
        if (columns.isEmpty()) {
            throw badRequest("唯一约束字段不能为空");
        }
        for (String column : columns) {
            validateIdentifier(column, "唯一约束字段列名非法");
        }
        String indexColumns = String.join(", ", columns);
        dropIndexIfExists(constraint.indexName());
        archiveMapper.executeSql(
                "create unique index %s on %s (%s) where deleted_flag = false"
                        .formatted(constraint.indexName(), tableName, indexColumns));
    }

    private void dropIndexIfExists(String indexName) {
        if (StringUtils.isBlank(indexName)) {
            return;
        }
        validateIdentifier(indexName, "索引名非法");
        archiveMapper.executeSql("drop index if exists %s".formatted(indexName));
    }

    private String uniqueConstraintIndexName(
            String categoryCode, ArchiveLevel archiveLevel, String constraintCode) {
        String seed =
                categoryCode.toLowerCase(java.util.Locale.ROOT)
                        + "_"
                        + archiveLevel.value()
                        + "_"
                        + constraintCode;
        return ArchiveDynamicTableNames.stableIdentifier("uk_am_archive_constraint_", seed);
    }

    private FieldValues validateFieldRequest(ArchiveFieldRequest request) {
        validateRequired(request.fieldCode(), "字段编码不能为空");
        validateRequired(request.fieldName(), "字段名称不能为空");
        String fieldCode = request.fieldCode().trim();
        if (!FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            throw badRequest("字段编码只允许小写字母、数字和下划线，并且必须以小写字母开头");
        }
        if (RESERVED_RECORD_FIELD_CODES.contains(fieldCode)) {
            throw badRequest("字段编码属于档案记录固定字段，不能作为动态字段：" + fieldCode);
        }
        if (toColumnName(fieldCode).length() > POSTGRESQL_IDENTIFIER_LIMIT) {
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
        return new FieldValues(
                normalizeArchiveLevel(request.archiveLevel()),
                normalizeFieldScope(request.fieldScope()),
                fieldCode,
                request.fieldName().trim(),
                fieldType,
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
                request.enabled() == null || request.enabled(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private ArchiveLevel normalizeArchiveLevel(@Nullable ArchiveLevel archiveLevel) {
        return ArchiveDynamicTableNames.normalizeArchiveLevel(archiveLevel);
    }

    private ArchiveFieldScope normalizeFieldScope(@Nullable ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.normalizeFieldScope(fieldScope);
    }

    private void ensureArchiveLevelAllowed(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        if (!ArchiveDynamicTableNames.supportsArchiveLevel(category, archiveLevel)) {
            throw badRequest("该分类未启用案卷管理");
        }
    }

    private boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private boolean isDynamicTableBuilt(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        return StringUtils.isNotBlank(tableName) && archiveMapper.tableExists(tableName) > 0;
    }

    private ArchiveManagementMode normalizeManagementMode(
            @Nullable ArchiveManagementMode managementMode) {
        return managementMode == null ? ArchiveManagementMode.ITEM_ONLY : managementMode;
    }

    private void applyFieldValues(ArchiveField field, Long categoryId, FieldValues values) {
        field.setCategoryId(categoryId);
        field.setArchiveLevel(values.archiveLevel());
        field.setFieldScope(values.fieldScope());
        field.setFieldCode(values.fieldCode());
        field.setFieldName(values.fieldName());
        field.setFieldType(values.fieldType());
        field.setColumnName(toColumnName(values.fieldCode()));
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
        field.setEnabled(values.enabled());
        field.setSortOrder(values.sortOrder());
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

    private int layoutOrder(@Nullable Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
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

    private List<ArchiveFieldDto> applyEffectiveLayout(
            Long categoryId, ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        Map<Long, ArchiveFieldLayoutItemDto> layoutsByFieldId =
                publicLayoutItems(categoryId, surface, fields).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldLayoutItemDto::fieldId, item -> item));
        return fields.stream()
                .map(field -> applyLayout(field, surface, layoutsByFieldId.get(field.id())))
                .sorted(
                        java.util.Comparator.comparingInt(
                                        (ArchiveFieldDto field) -> layoutOrder(surface, field))
                                .thenComparing(ArchiveFieldDto::id))
                .toList();
    }

    private List<ArchiveFieldLayoutItemDto> publicLayoutItems(
            Long categoryId, ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        List<ArchiveFieldLayoutItemDto> publicItems = layoutItems(categoryId, surface, fields);
        return publicItems.isEmpty() ? defaultLayoutItems(surface, fields) : publicItems;
    }

    private List<ArchiveFieldLayoutItemDto> layoutItems(
            Long categoryId, ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        Map<Long, ArchiveFieldDto> fieldsById =
                fields.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldDto::id, field -> field));
        ArchiveLevel archiveLevel =
                fields.isEmpty() ? ArchiveLevel.ITEM : fields.getFirst().archiveLevel();
        ArchiveFieldScope fieldScope =
                fields.isEmpty() ? ArchiveFieldScope.METADATA : fields.getFirst().fieldScope();
        return fieldLayoutRepository.list(categoryId, surface).stream()
                .filter(layout -> fieldsById.containsKey(layout.getFieldId()))
                .filter(
                        layout ->
                                fieldsById.get(layout.getFieldId()).archiveLevel() == archiveLevel)
                .filter(layout -> fieldsById.get(layout.getFieldId()).fieldScope() == fieldScope)
                .map(layout -> mapFieldLayoutItem(layout, fieldsById.get(layout.getFieldId())))
                .toList();
    }

    private List<ArchiveFieldLayoutItemDto> defaultLayoutItems(
            ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        return fields.stream()
                .map(
                        field ->
                                new ArchiveFieldLayoutItemDto(
                                        field.id(),
                                        field.fieldCode(),
                                        field.fieldName(),
                                        field.fieldType(),
                                        field.editControl(),
                                        surfaceVisible(surface, field),
                                        surface == ArchiveLayoutSurface.TABLE
                                                ? field.listWidth()
                                                : null,
                                        surfaceColSpan(surface, field),
                                        layoutOrder(surface, field),
                                        0))
                .sorted(
                        java.util.Comparator.comparingInt(ArchiveFieldLayoutItemDto::rowOrder)
                                .thenComparing(ArchiveFieldLayoutItemDto::fieldId))
                .toList();
    }

    private void saveFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            ArchiveFieldLayoutRequest request,
            Long userId) {
        requireId(categoryId);
        ArchiveCategoryDto category = getCategory(categoryId);
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        ArchiveFieldScope normalizedScope = normalizeFieldScope(fieldScope);
        ensureArchiveLevelAllowed(category, normalizedLevel);
        List<@Nullable ArchiveFieldLayoutItemRequest> items =
                request == null || request.items() == null ? List.of() : request.items();
        Map<Long, ArchiveFieldDto> fieldsById =
                listEnabledFields(categoryId, normalizedLevel, normalizedScope).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldDto::id, field -> field));
        Set<Long> seenFieldIds = new HashSet<>();
        fieldLayoutRepository.list(categoryId, surface).stream()
                .filter(layout -> fieldsById.containsKey(layout.getFieldId()))
                .forEach(
                        layout -> {
                            layout.setUpdatedBy(userId);
                            fieldLayoutRepository.update(layout);
                            fieldLayoutRepository.delete(layout);
                        });
        for (@Nullable ArchiveFieldLayoutItemRequest item : items) {
            if (item == null || item.fieldId() == null || !fieldsById.containsKey(item.fieldId())) {
                throw badRequest("布局字段只能选择当前分类字段");
            }
            if (!seenFieldIds.add(item.fieldId())) {
                throw badRequest("布局字段不能重复");
            }
            ArchiveFieldLayout layout = new ArchiveFieldLayout();
            layout.setCategoryId(categoryId);
            layout.setSurface(surface);
            layout.setFieldId(item.fieldId());
            layout.setVisible(item.visible() == null || item.visible());
            layout.setListWidth(
                    surface == ArchiveLayoutSurface.TABLE
                            ? normalizeListWidth(item.listWidth())
                            : null);
            layout.setColSpan(normalizeColSpan(item.colSpan()));
            layout.setRowOrder(item.rowOrder() == null ? 0 : item.rowOrder());
            layout.setColOrder(item.colOrder() == null ? 0 : item.colOrder());
            layout.setCreatedBy(userId);
            layout.setUpdatedBy(userId);
            fieldLayoutRepository.insert(layout);
        }
    }

    private ArchiveFieldDto applyLayout(
            ArchiveFieldDto field,
            ArchiveLayoutSurface surface,
            @Nullable ArchiveFieldLayoutItemDto item) {
        if (item == null) {
            return field;
        }
        return switch (surface) {
            case TABLE ->
                    copyField(
                            field,
                            item.visible(),
                            item.listWidth(),
                            item.rowOrder(),
                            field.detailVisible(),
                            field.detailColSpan(),
                            field.detailSortOrder(),
                            field.editVisible(),
                            field.editColSpan(),
                            field.editSortOrder());
            case DETAIL ->
                    copyField(
                            field,
                            field.listVisible(),
                            field.listWidth(),
                            field.listSortOrder(),
                            item.visible(),
                            item.colSpan(),
                            item.rowOrder(),
                            field.editVisible(),
                            field.editColSpan(),
                            field.editSortOrder());
            case EDIT ->
                    copyField(
                            field,
                            field.listVisible(),
                            field.listWidth(),
                            field.listSortOrder(),
                            field.detailVisible(),
                            field.detailColSpan(),
                            field.detailSortOrder(),
                            item.visible(),
                            item.colSpan(),
                            item.rowOrder());
        };
    }

    private ArchiveFieldDto copyField(
            ArchiveFieldDto field,
            boolean listVisible,
            Integer listWidth,
            int listSortOrder,
            boolean detailVisible,
            int detailColSpan,
            int detailSortOrder,
            boolean editVisible,
            int editColSpan,
            int editSortOrder) {
        return new ArchiveFieldDto(
                field.id(),
                field.categoryId(),
                field.archiveLevel(),
                field.fieldScope(),
                field.fieldCode(),
                field.fieldName(),
                field.fieldType(),
                field.columnName(),
                field.textLength(),
                field.decimalPrecision(),
                field.decimalScale(),
                field.editControl(),
                listVisible,
                listWidth,
                listSortOrder,
                detailVisible,
                detailColSpan,
                detailSortOrder,
                editVisible,
                editColSpan,
                editSortOrder,
                field.exactSearchable(),
                field.enabled(),
                field.sortOrder(),
                field.createdAt(),
                field.updatedAt());
    }

    private boolean surfaceVisible(ArchiveLayoutSurface surface, ArchiveFieldDto field) {
        return switch (surface) {
            case TABLE -> field.listVisible();
            case DETAIL -> field.detailVisible();
            case EDIT -> field.editVisible();
        };
    }

    private int surfaceColSpan(ArchiveLayoutSurface surface, ArchiveFieldDto field) {
        return switch (surface) {
            case TABLE -> 1;
            case DETAIL -> field.detailColSpan();
            case EDIT -> field.editColSpan();
        };
    }

    private int layoutOrder(ArchiveLayoutSurface surface, ArchiveFieldDto field) {
        return switch (surface) {
            case TABLE -> field.listSortOrder();
            case DETAIL -> field.detailSortOrder();
            case EDIT -> field.editSortOrder();
        };
    }

    private UniqueConstraintValues validateUniqueConstraintRequest(
            ArchiveCategoryDto category, ArchiveUniqueConstraintRequest request) {
        validateRequired(request.constraintCode(), "约束编码不能为空");
        validateRequired(request.constraintName(), "约束名称不能为空");
        String constraintCode = request.constraintCode().trim();
        if (!FIELD_CODE_PATTERN.matcher(constraintCode).matches()) {
            throw badRequest("约束编码只允许小写字母、数字和下划线，并且必须以小写字母开头");
        }
        List<Long> fieldIds = request.fieldIds() == null ? List.of() : request.fieldIds();
        if (fieldIds.isEmpty()) {
            throw badRequest("唯一约束字段不能为空");
        }
        if (new HashSet<>(fieldIds).size() != fieldIds.size()) {
            throw badRequest("唯一约束字段不能重复");
        }
        ArchiveLevel archiveLevel = normalizeArchiveLevel(request.archiveLevel());
        ensureArchiveLevelAllowed(category, archiveLevel);
        Map<Long, ArchiveFieldDto> fieldsById =
                listFields(category.id()).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldDto::id, field -> field));
        for (Long fieldId : fieldIds) {
            ArchiveFieldDto field = fieldsById.get(fieldId);
            if (field == null) {
                throw badRequest("唯一约束只能选择当前分类字段");
            }
            if (field.archiveLevel() != archiveLevel) {
                throw badRequest("唯一约束字段必须和约束层级一致");
            }
            if (field.fieldScope() != ArchiveFieldScope.METADATA) {
                throw badRequest("唯一约束字段必须是案卷或卷内电子字段");
            }
        }
        return new UniqueConstraintValues(
                archiveLevel,
                constraintCode,
                request.constraintName().trim(),
                request.enabled() == null || request.enabled(),
                fieldIds);
    }

    private String toColumnName(String fieldCode) {
        return "f_" + fieldCode;
    }

    private String sqlType(ArchiveFieldDto field) {
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

    private void validateParentCategory(@Nullable Long categoryId, @Nullable Long parentId) {
        if (parentId == null) {
            return;
        }
        requireId(parentId);
        getCategory(parentId);
        if (categoryId == null) {
            return;
        }
        if (categoryId.equals(parentId)) {
            throw badRequest("不能将分类自身设置为父级");
        }
        Long currentParentId = parentId;
        while (currentParentId != null) {
            if (categoryId.equals(currentParentId)) {
                throw badRequest("不能将子分类设置为父级");
            }
            currentParentId = archiveMapper.findParentId(currentParentId);
        }
    }

    private void validateIdentifier(String value, String message) {
        if (StringUtils.isBlank(value)
                || value.length() > POSTGRESQL_IDENTIFIER_LIMIT
                || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw badRequest(message);
        }
    }

    private void validateRequired(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw badRequest(message);
        }
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("ID 不合法");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ArchiveFondsDto mapFonds(Map<String, Object> row) {
        return new ArchiveFondsDto(
                number(row, "id").longValue(),
                string(row, "fondsCode"),
                string(row, "fondsName"),
                bool(row, "enabled"),
                number(row, "sortOrder").intValue(),
                dateTime(row, "createdAt"),
                dateTime(row, "updatedAt"));
    }

    private ArchiveFondsDto mapFonds(ArchiveFonds fonds) {
        return new ArchiveFondsDto(
                fonds.getId(),
                fonds.getFondsCode(),
                fonds.getFondsName(),
                fonds.isEnabled(),
                fonds.getSortOrder(),
                fonds.getCreatedAt(),
                fonds.getUpdatedAt());
    }

    private ArchiveCategoryDto mapCategory(Map<String, Object> row) {
        Number parentId = numberOrNull(row, "parentId");
        return new ArchiveCategoryDto(
                number(row, "id").longValue(),
                parentId == null ? null : parentId.longValue(),
                string(row, "categoryCode"),
                string(row, "categoryName"),
                ArchiveManagementMode.fromValue(string(row, "managementMode")),
                string(row, "volumeTableName"),
                string(row, "itemTableName"),
                string(row, "volumePhysicalTableName"),
                string(row, "itemPhysicalTableName"),
                ArchiveTableStatus.fromValue(string(row, "tableStatus")),
                dateTime(row, "builtAt"),
                bool(row, "enabled"),
                number(row, "sortOrder").intValue(),
                dateTime(row, "createdAt"),
                dateTime(row, "updatedAt"));
    }

    private ArchiveCategoryDto mapCategory(ArchiveCategory category) {
        return new ArchiveCategoryDto(
                category.getId(),
                category.getParentId(),
                category.getCategoryCode(),
                category.getCategoryName(),
                category.getManagementMode(),
                category.getVolumeTableName(),
                category.getItemTableName(),
                category.getVolumePhysicalTableName(),
                category.getItemPhysicalTableName(),
                category.getTableStatus(),
                category.getBuiltAt(),
                category.isEnabled(),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private ArchiveFieldDto mapField(ArchiveField field) {
        return new ArchiveFieldDto(
                field.getId(),
                field.getCategoryId(),
                field.getArchiveLevel(),
                field.getFieldScope(),
                field.getFieldCode(),
                field.getFieldName(),
                field.getFieldType(),
                field.getColumnName(),
                field.getTextLength(),
                field.getDecimalPrecision(),
                field.getDecimalScale(),
                field.getEditControl(),
                field.isListVisible(),
                field.getListWidth(),
                field.getListSortOrder(),
                field.isDetailVisible(),
                field.getDetailColSpan(),
                field.getDetailSortOrder(),
                field.isEditVisible(),
                field.getEditColSpan(),
                field.getEditSortOrder(),
                field.isExactSearchable(),
                field.isEnabled(),
                field.getSortOrder(),
                field.getCreatedAt(),
                field.getUpdatedAt());
    }

    private ArchiveFieldLayoutItemDto mapFieldLayoutItem(
            ArchiveFieldLayout layout, ArchiveFieldDto field) {
        return new ArchiveFieldLayoutItemDto(
                layout.getFieldId(),
                field.fieldCode(),
                field.fieldName(),
                field.fieldType(),
                field.editControl(),
                layout.isVisible(),
                layout.getListWidth() == null ? field.listWidth() : layout.getListWidth(),
                layout.getColSpan(),
                layout.getRowOrder(),
                layout.getColOrder());
    }

    private ArchiveUniqueConstraintDto mapUniqueConstraint(
            Map<String, @Nullable Object> row, List<ArchiveUniqueConstraintFieldDto> fields) {
        return new ArchiveUniqueConstraintDto(
                number(row, "id").longValue(),
                number(row, "categoryId").longValue(),
                ArchiveLevel.fromValue(string(row, "archiveLevel")),
                string(row, "constraintCode"),
                string(row, "constraintName"),
                string(row, "indexName"),
                bool(row, "enabled"),
                fields,
                dateTime(row, "createdAt"),
                dateTime(row, "updatedAt"));
    }

    private ArchiveUniqueConstraintFieldDto mapUniqueConstraintField(
            Map<String, @Nullable Object> row) {
        return new ArchiveUniqueConstraintFieldDto(
                number(row, "fieldId").longValue(),
                number(row, "fieldOrder").intValue(),
                ArchiveLevel.fromValue(string(row, "archiveLevel")),
                string(row, "fieldCode"),
                string(row, "fieldName"),
                string(row, "columnName"));
    }

    private @Nullable String string(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private boolean bool(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, @Nullable Object> row, String key) {
        Number number = numberOrNull(row, key);
        if (number == null) {
            throw new IllegalStateException("缺少数值字段：" + key);
        }
        return number;
    }

    private @Nullable Number numberOrNull(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number : null;
    }

    private @Nullable Integer integerOrNull(Map<String, @Nullable Object> row, String key) {
        Number value = numberOrNull(row, key);
        return value == null ? null : value.intValue();
    }

    private @Nullable LocalDateTime dateTime(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveFondsRequest(
            @Nullable String fondsCode,
            @Nullable String fondsName,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveFondsDto(
            Long id,
            String fondsCode,
            String fondsName,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveCategoryRequest(
            @Nullable String categoryCode,
            @Nullable String categoryName,
            @Nullable Long parentId,
            @Nullable ArchiveManagementMode managementMode,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveCategoryDto(
            Long id,
            @Nullable Long parentId,
            String categoryCode,
            String categoryName,
            ArchiveManagementMode managementMode,
            @Nullable String volumeTableName,
            @Nullable String itemTableName,
            @Nullable String volumePhysicalTableName,
            @Nullable String itemPhysicalTableName,
            ArchiveTableStatus tableStatus,
            @Nullable LocalDateTime builtAt,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveFieldRequest(
            @Nullable ArchiveLevel archiveLevel,
            @Nullable ArchiveFieldScope fieldScope,
            @Nullable String fieldCode,
            @Nullable String fieldName,
            @Nullable ArchiveFieldType fieldType,
            @Nullable Integer textLength,
            @Nullable Integer decimalPrecision,
            @Nullable Integer decimalScale,
            @Nullable ArchiveFieldControl editControl,
            @Nullable Boolean listVisible,
            @Nullable Integer listWidth,
            @Nullable Integer listSortOrder,
            @Nullable Boolean detailVisible,
            @Nullable Integer detailColSpan,
            @Nullable Integer detailSortOrder,
            @Nullable Boolean editVisible,
            @Nullable Integer editColSpan,
            @Nullable Integer editSortOrder,
            @Nullable Boolean exactSearchable,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveFieldDto(
            Long id,
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            String columnName,
            @Nullable Integer textLength,
            @Nullable Integer decimalPrecision,
            @Nullable Integer decimalScale,
            ArchiveFieldControl editControl,
            boolean listVisible,
            @Nullable Integer listWidth,
            int listSortOrder,
            boolean detailVisible,
            int detailColSpan,
            int detailSortOrder,
            boolean editVisible,
            int editColSpan,
            int editSortOrder,
            boolean exactSearchable,
            boolean enabled,
            int sortOrder,
            @Nullable LocalDateTime createdAt,
            @Nullable LocalDateTime updatedAt) {}

    public record ArchiveFieldLayoutDto(
            ArchiveLayoutSurface surface, String scope, List<ArchiveFieldLayoutItemDto> items) {}

    public record ArchiveFieldLayoutItemDto(
            Long fieldId,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            ArchiveFieldControl editControl,
            boolean visible,
            @Nullable Integer listWidth,
            int colSpan,
            int rowOrder,
            int colOrder) {}

    public record ArchiveFieldLayoutRequest(
            @Nullable List<@Nullable ArchiveFieldLayoutItemRequest> items) {}

    public record ArchiveFieldLayoutItemRequest(
            @Nullable Long fieldId,
            @Nullable Boolean visible,
            @Nullable Integer listWidth,
            @Nullable Integer colSpan,
            @Nullable Integer rowOrder,
            @Nullable Integer colOrder) {}

    public record ArchiveUniqueConstraintRequest(
            @Nullable ArchiveLevel archiveLevel,
            @Nullable String constraintCode,
            @Nullable String constraintName,
            @Nullable Boolean enabled,
            @Nullable List<Long> fieldIds) {}

    public record ArchiveUniqueConstraintDto(
            Long id,
            Long categoryId,
            ArchiveLevel archiveLevel,
            String constraintCode,
            String constraintName,
            @Nullable String indexName,
            boolean enabled,
            List<ArchiveUniqueConstraintFieldDto> fields,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveUniqueConstraintFieldDto(
            Long fieldId,
            int fieldOrder,
            ArchiveLevel archiveLevel,
            String fieldCode,
            String fieldName,
            String columnName) {}

    private record FieldValues(
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
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
            boolean enabled,
            int sortOrder) {}

    private record UniqueConstraintValues(
            ArchiveLevel archiveLevel,
            String constraintCode,
            String constraintName,
            boolean enabled,
            List<Long> fieldIds) {}
}
