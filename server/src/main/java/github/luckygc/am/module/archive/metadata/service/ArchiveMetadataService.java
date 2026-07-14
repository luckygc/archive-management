package github.luckygc.am.module.archive.metadata.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldSource;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;

@Service
public class ArchiveMetadataService extends ArchiveMetadataTypes {

    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final List<BuiltinDataScopeField> BUILTIN_DATA_SCOPE_FIELDS =
            List.of(
                    new BuiltinDataScopeField(
                            "archive_year", "年度", ArchiveFieldType.INTEGER, "archive_year"),
                    new BuiltinDataScopeField(
                            "retention_period_id",
                            "保管期限",
                            ArchiveFieldType.INTEGER,
                            "retention_period_id"),
                    new BuiltinDataScopeField(
                            "electronic_status",
                            "电子状态",
                            ArchiveFieldType.TEXT,
                            "electronic_status"),
                    new BuiltinDataScopeField(
                            "fonds_code", "全宗编码", ArchiveFieldType.TEXT, "fonds_code"),
                    new BuiltinDataScopeField(
                            "category_code", "分类编码", ArchiveFieldType.TEXT, "category_code"),
                    new BuiltinDataScopeField(
                            "security_level_id",
                            "密级",
                            ArchiveFieldType.INTEGER,
                            "security_level_id"),
                    new BuiltinDataScopeField(
                            "created_by", "创建人", ArchiveFieldType.INTEGER, "created_by"));

    private final ArchiveMapper archiveMapper;
    private final ArchiveFieldDataRepository fieldRepository;
    private final ArchiveFieldDefinitionService fieldDefinitionService;
    private final ArchiveDynamicTableService dynamicTableService;
    private final ArchiveFieldLayoutService fieldLayoutService;
    private final ArchiveUniqueConstraintService uniqueConstraintService;
    private final ArchiveCategoryService categoryService;

    public ArchiveMetadataService(
            ArchiveMapper archiveMapper,
            ArchiveFieldDataRepository fieldRepository,
            ArchiveFieldDefinitionService fieldDefinitionService,
            ArchiveDynamicTableService dynamicTableService,
            ArchiveFieldLayoutService fieldLayoutService,
            ArchiveUniqueConstraintService uniqueConstraintService,
            ArchiveCategoryService categoryService) {
        this.archiveMapper = archiveMapper;
        this.fieldRepository = fieldRepository;
        this.fieldDefinitionService = fieldDefinitionService;
        this.dynamicTableService = dynamicTableService;
        this.fieldLayoutService = fieldLayoutService;
        this.uniqueConstraintService = uniqueConstraintService;
        this.categoryService = categoryService;
    }

    public List<ArchiveFieldDto> listFields(Long categoryId) {
        return listFieldsInternal(categoryId);
    }

    private List<ArchiveFieldDto> listFieldsInternal(Long categoryId) {
        requireId(categoryId);
        return fieldRepository.list(categoryId).stream().map(this::mapField).toList();
    }

    public List<ArchiveFieldDto> listFields(Long categoryId, ArchiveLevel archiveLevel) {
        requireId(categoryId);
        return fieldRepository
                .list(categoryId, fieldDefinitionService.normalizeArchiveLevel(archiveLevel), true)
                .stream()
                .map(this::mapField)
                .toList();
    }

    public List<ArchiveFieldDto> listEnabledFields(Long categoryId) {
        return listEnabledFieldsInternal(categoryId, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA);
    }

    public List<ArchiveFieldDto> listEnabledFields(Long categoryId, ArchiveLevel archiveLevel) {
        return listEnabledFieldsInternal(categoryId, archiveLevel, ArchiveFieldScope.METADATA);
    }

    public List<ArchiveFieldDto> listEnabledFields(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return listEnabledFieldsInternal(categoryId, archiveLevel, fieldScope);
    }

    private List<ArchiveFieldDto> listEnabledFieldsInternal(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        requireId(categoryId);
        return fieldRepository
                .list(
                        categoryId,
                        fieldDefinitionService.normalizeArchiveLevel(archiveLevel),
                        fieldDefinitionService.normalizeFieldScope(fieldScope),
                        true)
                .stream()
                .map(this::mapField)
                .toList();
    }

    public List<ArchiveFieldDto> listBuiltinDataScopeFields() {
        return BUILTIN_DATA_SCOPE_FIELDS.stream().map(this::toBuiltinFieldDto).toList();
    }

    public List<ArchiveFieldDto> listEffectiveFields(
            Long categoryId, ArchiveLayoutSurface surface, Long userId) {
        return listEffectiveFieldsInternal(
                categoryId, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA, surface, userId);
    }

    public List<ArchiveFieldDto> listEffectiveFields(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveLayoutSurface surface, Long userId) {
        return listEffectiveFieldsInternal(
                categoryId, archiveLevel, ArchiveFieldScope.METADATA, surface, userId);
    }

    public List<ArchiveFieldDto> listEffectiveFields(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            Long userId) {
        return listEffectiveFieldsInternal(categoryId, archiveLevel, fieldScope, surface, userId);
    }

    private List<ArchiveFieldDto> listEffectiveFieldsInternal(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            Long userId) {
        List<ArchiveFieldDto> fields =
                listEnabledFieldsInternal(categoryId, archiveLevel, fieldScope);
        return fieldLayoutService.applyEffectiveLayout(categoryId, surface, fields);
    }

    public ArchiveFieldLayoutDto getFieldLayout(
            Long categoryId, ArchiveLevel archiveLevel, ArchiveLayoutSurface surface) {
        return getFieldLayoutInternal(
                categoryId, archiveLevel, ArchiveFieldScope.METADATA, surface);
    }

    public ArchiveFieldLayoutDto getFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface) {
        return getFieldLayoutInternal(categoryId, archiveLevel, fieldScope, surface);
    }

    private ArchiveFieldLayoutDto getFieldLayoutInternal(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface) {
        requireId(categoryId);
        categoryService.getCategory(categoryId);
        ArchiveLevel normalizedLevel = fieldDefinitionService.normalizeArchiveLevel(archiveLevel);
        ArchiveFieldScope normalizedScope = fieldDefinitionService.normalizeFieldScope(fieldScope);
        List<ArchiveFieldDto> fields =
                listEnabledFieldsInternal(categoryId, normalizedLevel, normalizedScope);
        return new ArchiveFieldLayoutDto(
                surface,
                "public",
                fieldLayoutService.publicLayoutItems(categoryId, surface, fields));
    }

    @Transactional
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveLayoutSurface surface,
            ArchiveFieldLayoutRequest request,
            Long userId) {
        return savePublicFieldLayoutInternal(
                categoryId, archiveLevel, ArchiveFieldScope.METADATA, surface, request, userId);
    }

    @Transactional
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            @Nullable ArchiveFieldLayoutRequest request,
            Long userId) {
        return savePublicFieldLayoutInternal(
                categoryId, archiveLevel, fieldScope, surface, request, userId);
    }

    private ArchiveFieldLayoutDto savePublicFieldLayoutInternal(
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            ArchiveLayoutSurface surface,
            @Nullable ArchiveFieldLayoutRequest request,
            Long userId) {
        requireId(categoryId);
        ArchiveCategoryDto category = categoryService.getCategory(categoryId);
        ArchiveLevel normalizedLevel = fieldDefinitionService.normalizeArchiveLevel(archiveLevel);
        ArchiveFieldScope normalizedScope = fieldDefinitionService.normalizeFieldScope(fieldScope);
        fieldDefinitionService.ensureArchiveLevelAllowed(category, normalizedLevel);
        fieldLayoutService.savePublicLayout(
                categoryId,
                surface,
                listEnabledFieldsInternal(categoryId, normalizedLevel, normalizedScope),
                request);
        return getFieldLayoutInternal(categoryId, normalizedLevel, normalizedScope, surface);
    }

    @Transactional
    public ArchiveFieldDto createField(Long categoryId, ArchiveFieldRequest request, Long userId) {
        requireId(categoryId);
        ArchiveCategoryDto category = categoryService.getCategory(categoryId);
        ArchiveFieldDefinitionService.ArchiveFieldValues values =
                fieldDefinitionService.validate(request);
        fieldDefinitionService.ensureArchiveLevelAllowed(category, values.archiveLevel());
        ArchiveField field = new ArchiveField();
        fieldDefinitionService.applyValues(field, categoryId, values);
        return mapField(fieldRepository.insert(field));
    }

    @Transactional
    public ArchiveFieldDto updateField(
            Long categoryId, Long fieldId, ArchiveFieldRequest request, Long userId) {
        requireId(categoryId);
        requireId(fieldId);
        ArchiveFieldDefinitionService.ArchiveFieldValues values =
                fieldDefinitionService.validate(request);
        ArchiveCategoryDto category = categoryService.getCategory(categoryId);
        fieldDefinitionService.ensureArchiveLevelAllowed(category, values.archiveLevel());
        ArchiveFieldDto current = loadField(fieldId);
        if (!current.categoryId().equals(categoryId)) {
            throw notFound("字段定义不存在");
        }
        if (!current.fieldType().equals(values.fieldType())) {
            throw badRequest("已建字段不允许修改字段类型");
        }
        if (current.archiveLevel() != values.archiveLevel()
                && dynamicTableService.isDynamicTableBuilt(category, current.archiveLevel())) {
            throw badRequest("已建字段不允许修改适用层级");
        }
        ArchiveField field =
                fieldRepository.findById(fieldId).orElseThrow(() -> notFound("字段定义不存在"));
        fieldDefinitionService.applyValues(field, categoryId, values);
        ArchiveFieldDto updatedField = mapField(fieldRepository.update(field));
        dynamicTableService.syncDynamicColumnAfterFieldUpdate(category, current, updatedField);
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
        fieldRepository.update(field);
        fieldRepository.delete(field);
    }

    public ArchiveFieldDto getField(Long id) {
        return loadField(id);
    }

    private ArchiveFieldDto loadField(Long id) {
        requireId(id);
        return fieldRepository
                .findById(id)
                .map(this::mapField)
                .orElseThrow(() -> notFound("字段定义不存在"));
    }

    @Transactional
    public ArchiveCategoryDto buildTable(Long categoryId) {
        return buildTableInternal(categoryId, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA, null);
    }

    @Transactional
    public ArchiveCategoryDto buildTable(Long categoryId, ArchiveLevel requestedLevel) {
        return buildTableInternal(categoryId, requestedLevel, ArchiveFieldScope.METADATA, null);
    }

    @Transactional
    public ArchiveCategoryDto buildTable(
            Long categoryId, ArchiveLevel requestedLevel, Long userId) {
        return buildTableInternal(categoryId, requestedLevel, ArchiveFieldScope.METADATA, userId);
    }

    @Transactional
    public ArchiveCategoryDto buildTable(
            Long categoryId,
            ArchiveLevel requestedLevel,
            ArchiveFieldScope requestedScope,
            Long userId) {
        return buildTableInternal(categoryId, requestedLevel, requestedScope, userId);
    }

    private ArchiveCategoryDto buildTableInternal(
            Long categoryId,
            ArchiveLevel requestedLevel,
            ArchiveFieldScope requestedScope,
            @Nullable Long userId) {
        ArchiveCategoryDto category = categoryService.getCategory(categoryId);
        ArchiveLevel archiveLevel = fieldDefinitionService.normalizeArchiveLevel(requestedLevel);
        ArchiveFieldScope fieldScope = fieldDefinitionService.normalizeFieldScope(requestedScope);
        fieldDefinitionService.ensureArchiveLevelAllowed(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                listEnabledFieldsInternal(categoryId, archiveLevel, fieldScope);
        dynamicTableService.buildTable(
                category,
                archiveLevel,
                fieldScope,
                fields,
                listUniqueConstraintsInternal(categoryId),
                userId);
        return categoryService.getCategory(categoryId);
    }

    public List<ArchiveUniqueConstraintDto> listUniqueConstraints(Long categoryId) {
        return listUniqueConstraintsInternal(categoryId);
    }

    private List<ArchiveUniqueConstraintDto> listUniqueConstraintsInternal(Long categoryId) {
        requireId(categoryId);
        return uniqueConstraintService.list(categoryId);
    }

    @Transactional
    public ArchiveUniqueConstraintDto createUniqueConstraint(
            Long categoryId, ArchiveUniqueConstraintRequest request, Long userId) {
        requireId(categoryId);
        ArchiveCategoryDto category = categoryService.getCategory(categoryId);
        return uniqueConstraintService.create(
                category, listFieldsInternal(categoryId), request, userId);
    }

    @Transactional
    public ArchiveUniqueConstraintDto updateUniqueConstraint(
            Long categoryId,
            Long constraintId,
            ArchiveUniqueConstraintRequest request,
            Long userId) {
        requireId(categoryId);
        requireId(constraintId);
        ArchiveCategoryDto category = categoryService.getCategory(categoryId);
        ArchiveUniqueConstraintDto current = loadUniqueConstraint(constraintId);
        if (!current.categoryId().equals(categoryId)) {
            throw notFound("唯一约束不存在");
        }
        return uniqueConstraintService.update(
                category, current, listFieldsInternal(categoryId), request, userId);
    }

    @Transactional
    public void deleteUniqueConstraint(Long categoryId, Long constraintId, Long userId) {
        requireId(categoryId);
        requireId(constraintId);
        ArchiveUniqueConstraintDto constraint = loadUniqueConstraint(constraintId);
        if (!constraint.categoryId().equals(categoryId)) {
            throw notFound("唯一约束不存在");
        }
        uniqueConstraintService.delete(constraint, categoryId, userId);
    }

    public ArchiveUniqueConstraintDto getUniqueConstraint(Long id) {
        return loadUniqueConstraint(id);
    }

    private ArchiveUniqueConstraintDto loadUniqueConstraint(Long id) {
        requireId(id);
        ArchiveUniqueConstraintDto constraint = uniqueConstraintService.find(id);
        if (constraint == null) {
            throw notFound("唯一约束不存在");
        }
        return constraint;
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
                field.isDataScopeFilterable(),
                field.isEnabled(),
                field.getSortOrder(),
                ArchiveFieldSource.METADATA,
                field.getCreatedAt(),
                field.getUpdatedAt());
    }

    private ArchiveFieldDto toBuiltinFieldDto(BuiltinDataScopeField field) {
        return new ArchiveFieldDto(
                0L,
                0L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                field.fieldCode(),
                field.fieldName(),
                field.fieldType(),
                field.columnName(),
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
                false,
                true,
                true,
                0,
                ArchiveFieldSource.BUILTIN,
                null,
                null);
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

    private record BuiltinDataScopeField(
            String fieldCode, String fieldName, ArchiveFieldType fieldType, String columnName) {}
}
