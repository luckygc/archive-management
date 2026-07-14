package github.luckygc.am.module.archive.metadata.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveClassificationScheme;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldSource;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveRetentionPeriod;
import github.luckygc.am.module.archive.metadata.ArchiveSecurityLevel;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveClassificationSchemeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsCategoryScopeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveRetentionPeriodDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveSecurityLevelDataRepository;

@Service
public class ArchiveMetadataService {

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
    private final ArchiveFondsDataRepository fondsRepository;
    private final ArchiveClassificationSchemeDataRepository classificationSchemeRepository;
    private final ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository;
    private final ArchiveCategoryDataRepository categoryRepository;
    private final ArchiveFieldDataRepository fieldRepository;
    private final ArchiveSecurityLevelDataRepository securityLevelRepository;
    private final ArchiveRetentionPeriodDataRepository retentionPeriodRepository;
    private final ArchiveFieldDefinitionService fieldDefinitionService;
    private final ArchiveDynamicTableService dynamicTableService;
    private final ArchiveFieldLayoutService fieldLayoutService;
    private final ArchiveUniqueConstraintService uniqueConstraintService;
    private final ArchiveCategoryService categoryService;

    public ArchiveMetadataService(
            ArchiveMapper archiveMapper,
            ArchiveFondsDataRepository fondsRepository,
            ArchiveClassificationSchemeDataRepository classificationSchemeRepository,
            ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository,
            ArchiveCategoryDataRepository categoryRepository,
            ArchiveFieldDataRepository fieldRepository,
            ArchiveSecurityLevelDataRepository securityLevelRepository,
            ArchiveRetentionPeriodDataRepository retentionPeriodRepository,
            ArchiveFieldDefinitionService fieldDefinitionService,
            ArchiveDynamicTableService dynamicTableService,
            ArchiveFieldLayoutService fieldLayoutService,
            ArchiveUniqueConstraintService uniqueConstraintService,
            ArchiveCategoryService categoryService) {
        this.archiveMapper = archiveMapper;
        this.fondsRepository = fondsRepository;
        this.classificationSchemeRepository = classificationSchemeRepository;
        this.fondsCategoryScopeRepository = fondsCategoryScopeRepository;
        this.categoryRepository = categoryRepository;
        this.fieldRepository = fieldRepository;
        this.securityLevelRepository = securityLevelRepository;
        this.retentionPeriodRepository = retentionPeriodRepository;
        this.fieldDefinitionService = fieldDefinitionService;
        this.dynamicTableService = dynamicTableService;
        this.fieldLayoutService = fieldLayoutService;
        this.uniqueConstraintService = uniqueConstraintService;
        this.categoryService = categoryService;
    }

    public ArchiveFondsDto getFondsByCode(String fondsCode) {
        return mapFonds(loadFondsByCode(fondsCode));
    }

    public ArchiveFondsDto getFonds(Long id) {
        requireId(id);
        return fondsRepository
                .findById(id)
                .map(this::mapFonds)
                .orElseThrow(() -> notFound("全宗不存在"));
    }

    public ArchiveFondsDto getEnabledFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) {
            throw new BadRequestException("全宗不可用");
        }
        return fondsRepository
                .find(normalizedCode)
                .filter(ArchiveFonds::isEnabled)
                .map(this::mapFonds)
                .orElseThrow(() -> new BadRequestException("全宗不可用"));
    }

    public List<ArchiveClassificationSchemeDto> listClassificationSchemes(
            @Nullable Boolean enabled) {
        List<ArchiveClassificationScheme> schemes =
                enabled == null
                        ? classificationSchemeRepository.list()
                        : classificationSchemeRepository.list(enabled);
        return schemes.stream().map(this::mapClassificationScheme).toList();
    }

    @Transactional
    public ArchiveClassificationSchemeDto createClassificationScheme(
            ArchiveClassificationSchemeRequest request, Long userId) {
        ClassificationSchemeValues values = validateClassificationSchemeRequest(request);
        ensureSchemeCodeAvailable(values.schemeCode(), null);
        ArchiveClassificationScheme scheme = new ArchiveClassificationScheme();
        applyClassificationSchemeValues(scheme, values);
        return mapClassificationScheme(classificationSchemeRepository.insert(scheme));
    }

    @Transactional
    public ArchiveClassificationSchemeDto updateClassificationScheme(
            Long id, ArchiveClassificationSchemeRequest request, Long userId) {
        requireId(id);
        ClassificationSchemeValues values = validateClassificationSchemeRequest(request);
        ensureSchemeCodeAvailable(values.schemeCode(), id);
        ArchiveClassificationScheme scheme =
                classificationSchemeRepository.findById(id).orElseThrow(() -> notFound("分类方案不存在"));
        applyClassificationSchemeValues(scheme, values);
        return mapClassificationScheme(classificationSchemeRepository.update(scheme));
    }

    public List<ArchiveSecurityLevelDto> listSecurityLevels(@Nullable Boolean enabled) {
        List<ArchiveSecurityLevel> levels =
                enabled == null
                        ? securityLevelRepository.list()
                        : securityLevelRepository.list(enabled);
        return levels.stream().map(this::mapSecurityLevel).toList();
    }

    @Transactional
    public ArchiveSecurityLevelDto updateSecurityLevel(
            Long id, UpdateArchiveSecurityLevelRequest request) {
        requireId(id);
        String name = StringUtils.trimToNull(request.levelName());
        if (name == null) {
            throw new BadRequestException("密级名称不能为空", "levelName", "密级名称不能为空");
        }
        ArchiveSecurityLevel level =
                securityLevelRepository.findById(id).orElseThrow(() -> notFound("密级不存在"));
        level.setLevelName(name);
        return mapSecurityLevel(securityLevelRepository.update(level));
    }

    public List<ArchiveRetentionPeriodDto> listRetentionPeriods(@Nullable Boolean enabled) {
        List<ArchiveRetentionPeriod> periods =
                enabled == null
                        ? retentionPeriodRepository.list()
                        : retentionPeriodRepository.list(enabled);
        return periods.stream().map(this::mapRetentionPeriod).toList();
    }

    @Transactional
    public ArchiveRetentionPeriodDto updateRetentionPeriod(
            Long id, UpdateArchiveRetentionPeriodRequest request) {
        requireId(id);
        String name = StringUtils.trimToNull(request.periodName());
        if (name == null) {
            throw new BadRequestException("保管期限名称不能为空", "periodName", "保管期限名称不能为空");
        }
        ArchiveRetentionPeriod period =
                retentionPeriodRepository.findById(id).orElseThrow(() -> notFound("保管期限不存在"));
        period.setPeriodName(name);
        return mapRetentionPeriod(retentionPeriodRepository.update(period));
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

    private ClassificationSchemeValues validateClassificationSchemeRequest(
            ArchiveClassificationSchemeRequest request) {
        validateRequired(request.schemeCode(), "分类方案编码不能为空");
        validateRequired(request.schemeName(), "分类方案名称不能为空");
        String schemeCode = request.schemeCode().trim();
        if (!FIELD_CODE_PATTERN.matcher(schemeCode).matches()) {
            throw badRequest("分类方案编码只允许小写字母、数字和下划线，并且必须以小写字母开头");
        }
        return new ClassificationSchemeValues(
                schemeCode,
                request.schemeName().trim(),
                StringUtils.trimToNull(request.description()),
                request.enabled() == null || request.enabled(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void applyClassificationSchemeValues(
            ArchiveClassificationScheme scheme, ClassificationSchemeValues values) {
        scheme.setSchemeCode(values.schemeCode());
        scheme.setSchemeName(values.schemeName());
        scheme.setDescription(values.description());
        scheme.setEnabled(values.enabled());
        scheme.setSortOrder(values.sortOrder());
    }

    private void ensureSchemeCodeAvailable(String schemeCode, @Nullable Long currentId) {
        ArchiveClassificationScheme existing =
                classificationSchemeRepository.findBySchemeCode(schemeCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw badRequest("分类方案编码已存在");
        }
    }

    private ArchiveClassificationScheme loadClassificationScheme(@Nullable Long id) {
        if (id == null || id <= 0) {
            throw badRequest("分类方案不能为空");
        }
        return classificationSchemeRepository.findById(id).orElseThrow(() -> notFound("分类方案不存在"));
    }

    private ArchiveFonds loadFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) {
            throw badRequest("全宗编码不能为空");
        }
        return fondsRepository.find(normalizedCode).orElseThrow(() -> notFound("全宗不存在"));
    }

    private ArchiveFonds loadEnabledFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) {
            throw new BadRequestException("全宗不可用");
        }
        return fondsRepository
                .find(normalizedCode)
                .filter(ArchiveFonds::isEnabled)
                .orElseThrow(() -> new BadRequestException("全宗不可用"));
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

    private ArchiveClassificationSchemeDto mapClassificationScheme(
            ArchiveClassificationScheme scheme) {
        return new ArchiveClassificationSchemeDto(
                scheme.getId(),
                scheme.getSchemeCode(),
                scheme.getSchemeName(),
                scheme.getDescription(),
                scheme.isDefaultFlag(),
                scheme.isEnabled(),
                scheme.getSortOrder(),
                scheme.getCreatedAt(),
                scheme.getUpdatedAt());
    }

    private ArchiveSecurityLevelDto mapSecurityLevel(ArchiveSecurityLevel level) {
        return new ArchiveSecurityLevelDto(
                level.getId(),
                level.getLevelName(),
                level.isEnabled(),
                level.getSortOrder(),
                level.getCreatedAt(),
                level.getUpdatedAt());
    }

    private ArchiveRetentionPeriodDto mapRetentionPeriod(ArchiveRetentionPeriod period) {
        return new ArchiveRetentionPeriodDto(
                period.getId(),
                period.getPeriodName(),
                period.isEnabled(),
                period.getSortOrder(),
                period.getCreatedAt(),
                period.getUpdatedAt());
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

    public record ArchiveClassificationSchemeRequest(
            @Nullable String schemeCode,
            @Nullable String schemeName,
            @Nullable String description,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveClassificationSchemeDto(
            Long id,
            String schemeCode,
            String schemeName,
            @Nullable String description,
            boolean defaultFlag,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveFondsCategoryScopeRequest(
            @Nullable Long categoryId,
            @Nullable Boolean defaultFlag,
            @Nullable Integer sortOrder) {}

    public record ArchiveFondsCategoryScopeDto(
            @Nullable Long id,
            String fondsCode,
            Long categoryId,
            boolean defaultFlag,
            int sortOrder,
            @Nullable LocalDateTime createdAt,
            @Nullable LocalDateTime updatedAt) {}

    public record UpdateArchiveSecurityLevelRequest(@Nullable String levelName) {}

    public record ArchiveSecurityLevelDto(
            Long id,
            String levelName,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record UpdateArchiveRetentionPeriodRequest(@Nullable String periodName) {}

    public record ArchiveRetentionPeriodDto(
            Long id,
            String periodName,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveCategoryRequest(
            @Nullable Long schemeId,
            @Nullable String categoryCode,
            @Nullable String categoryName,
            @Nullable Long parentId,
            @Nullable ArchiveManagementMode managementMode,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveCategoryDto(
            Long id,
            @Nullable Long schemeId,
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
            @Nullable Boolean dataScopeFilterable,
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
            boolean dataScopeFilterable,
            boolean enabled,
            int sortOrder,
            @Nullable ArchiveFieldSource fieldSource,
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

    private record BuiltinDataScopeField(
            String fieldCode, String fieldName, ArchiveFieldType fieldType, String columnName) {}

    private record ClassificationSchemeValues(
            String schemeCode,
            String schemeName,
            @Nullable String description,
            boolean enabled,
            int sortOrder) {}
}
