package github.luckygc.am.module.archive.item.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.item.ArchiveItemRelationDirection;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.ArchiveItemQueryCondition;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.ArchiveItemRelatedGroup;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.ArchiveItemWhere;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.mapper.ArchiveSqlRelatedGroup;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintFieldDto;

@Service
class ArchiveItemSearchCriteriaCompiler {

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMapper archiveMapper;

    ArchiveItemSearchCriteriaCompiler(
            ArchiveMetadataService archiveMetadataService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveMapper archiveMapper) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveMapper = archiveMapper;
    }

    private String dynamicTableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return dynamicTableName(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private String dynamicTableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.tableName(category, archiveLevel, fieldScope);
    }

    private ArchiveLevel normalizeArchiveLevel(ArchiveLevel archiveLevel) {
        return ArchiveDynamicTableNames.normalizeArchiveLevel(archiveLevel);
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

    List<ArchiveSqlCondition> buildSearchConditions(
            Long categoryId,
            ArchiveLevel archiveLevel,
            List<ArchiveFieldDto> fields,
            @Nullable ArchiveItemWhere where) {
        Map<String, ArchiveFieldDto> fieldsByCode =
                fields.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldDto::fieldCode, field -> field));
        List<String> uniqueFieldCodes =
                archiveMetadataService.listUniqueConstraints(categoryId).stream()
                        .filter(ArchiveUniqueConstraintDto::enabled)
                        .filter(constraint -> constraint.archiveLevel() == archiveLevel)
                        .flatMap(constraint -> constraint.fields().stream())
                        .map(ArchiveUniqueConstraintFieldDto::fieldCode)
                        .distinct()
                        .toList();
        List<ArchiveSqlCondition> conditions = new ArrayList<>();
        conditions.addAll(
                buildWhereConditions(fieldsByCode, uniqueFieldCodes, where, "where.conditions"));
        return conditions;
    }

    private List<ArchiveSqlCondition> buildWhereConditions(
            Map<String, ArchiveFieldDto> fieldsByCode,
            List<String> uniqueFieldCodes,
            @Nullable ArchiveItemWhere where,
            String fieldPath) {
        if (where == null || where.conditions() == null || where.conditions().isEmpty()) {
            return List.of();
        }
        List<ArchiveSqlCondition> conditions = new ArrayList<>();
        for (ArchiveItemQueryCondition condition : where.conditions()) {
            String fieldCode =
                    condition == null ? null : StringUtils.trimToNull(condition.fieldCode());
            if (fieldCode == null) {
                continue;
            }
            ArchiveFieldDto field = fieldsByCode.get(fieldCode);
            validateSearchableField(field, fieldCode, uniqueFieldCodes);
            ArchiveSqlCondition sqlCondition = toSqlCondition(field, condition, fieldPath);
            if (sqlCondition.value() != null
                    || sqlCondition.endValue() != null
                    || sqlCondition.operator() == ArchiveItemQueryOperator.IS_EMPTY
                    || sqlCondition.operator() == ArchiveItemQueryOperator.IS_NOT_EMPTY) {
                conditions.add(sqlCondition);
            }
        }
        return conditions;
    }

    private void validateSearchableField(
            @Nullable ArchiveFieldDto field, String fieldCode, List<String> uniqueFieldCodes) {
        if (field == null
                || (!field.exactSearchable() && !uniqueFieldCodes.contains(field.fieldCode()))) {
            throw badRequest("字段不允许作为筛选条件：" + fieldCode);
        }
    }

    private ArchiveSqlCondition toSqlCondition(
            ArchiveFieldDto field, ArchiveItemQueryCondition condition, String fieldPath) {
        ArchiveItemQueryOperator operator =
                condition.op() == null ? ArchiveItemQueryOperator.EQ : condition.op();
        return switch (operator) {
            case EQ ->
                    new ArchiveSqlCondition(
                            field.columnName(),
                            ArchiveItemQueryOperator.EQ,
                            convertValue(field, condition.value()));
            case CONTAINS -> {
                ensureTextOperator(field, operator, fieldPath);
                String value = convertSearchTextValue(field, condition.value());
                yield new ArchiveSqlCondition(
                        field.columnName(),
                        ArchiveItemQueryOperator.CONTAINS,
                        value == null ? null : "%" + escapeLike(value) + "%");
            }
            case STARTS_WITH -> {
                ensureTextOperator(field, operator, fieldPath);
                String value = convertSearchTextValue(field, condition.value());
                yield new ArchiveSqlCondition(
                        field.columnName(),
                        ArchiveItemQueryOperator.STARTS_WITH,
                        value == null ? null : escapeLike(value) + "%");
            }
            case GTE ->
                    new ArchiveSqlCondition(
                            field.columnName(),
                            ArchiveItemQueryOperator.GTE,
                            convertValue(field, condition.value()));
            case LTE ->
                    new ArchiveSqlCondition(
                            field.columnName(),
                            ArchiveItemQueryOperator.LTE,
                            convertValue(field, condition.value()));
            case BETWEEN ->
                    new ArchiveSqlCondition(
                            field.columnName(),
                            ArchiveItemQueryOperator.BETWEEN,
                            convertValue(field, condition.startValue()),
                            convertValue(field, condition.endValue()));
            case IS_EMPTY -> {
                ensureTextOperator(field, operator, fieldPath);
                yield new ArchiveSqlCondition(
                        field.columnName(), ArchiveItemQueryOperator.IS_EMPTY, null);
            }
            case IS_NOT_EMPTY -> {
                ensureTextOperator(field, operator, fieldPath);
                yield new ArchiveSqlCondition(
                        field.columnName(), ArchiveItemQueryOperator.IS_NOT_EMPTY, null);
            }
            case IN, IS_NULL, IS_NOT_NULL ->
                    throw badRequest("不支持的筛选操作符", fieldPath + ".op", "不支持的筛选操作符");
        };
    }

    List<ArchiveSqlRelatedGroup> buildRelatedGroups(
            @Nullable List<@Nullable ArchiveItemRelatedGroup> relatedGroups, Long userId) {
        if (relatedGroups == null || relatedGroups.isEmpty()) {
            return List.of();
        }
        List<ArchiveSqlRelatedGroup> compiled = new ArrayList<>();
        for (ArchiveItemRelatedGroup group : relatedGroups) {
            if (group == null) {
                continue;
            }
            if (group.categoryId() == null) {
                throw badRequest("关联分类不能为空", "relatedGroups.categoryId", "关联分类不能为空");
            }
            ArchiveCategoryDto category = archiveCategoryService.getCategory(group.categoryId());
            ArchiveLevel archiveLevel = ArchiveLevel.ITEM;
            ensureArchiveLevelAllowed(category, archiveLevel);
            if (!isDynamicTableBuilt(category, archiveLevel)) {
                throw badRequest("关联分类尚未建表", "relatedGroups.categoryId", "关联分类尚未建表");
            }
            List<ArchiveFieldDto> fields =
                    archiveMetadataService.listEffectiveFields(
                            group.categoryId(), archiveLevel, ArchiveLayoutSurface.TABLE, userId);
            Map<String, ArchiveFieldDto> fieldsByCode =
                    fields.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            ArchiveFieldDto::fieldCode, field -> field));
            List<String> uniqueFieldCodes =
                    archiveMetadataService.listUniqueConstraints(group.categoryId()).stream()
                            .filter(ArchiveUniqueConstraintDto::enabled)
                            .filter(constraint -> constraint.archiveLevel() == archiveLevel)
                            .flatMap(constraint -> constraint.fields().stream())
                            .map(ArchiveUniqueConstraintFieldDto::fieldCode)
                            .distinct()
                            .toList();
            compiled.add(
                    new ArchiveSqlRelatedGroup(
                            dynamicTableName(category, archiveLevel),
                            category.categoryCode(),
                            normalizeRelationDirection(group.direction()),
                            buildWhereConditions(
                                    fieldsByCode,
                                    uniqueFieldCodes,
                                    group.where(),
                                    "relatedGroups.where")));
        }
        return compiled;
    }

    private ArchiveItemRelationDirection normalizeRelationDirection(
            @Nullable ArchiveItemRelationDirection direction) {
        return direction == null ? ArchiveItemRelationDirection.OUTGOING : direction;
    }

    private void ensureTextOperator(
            ArchiveFieldDto field, ArchiveItemQueryOperator operator, String fieldPath) {
        if (field.fieldType() != ArchiveFieldType.TEXT) {
            throw badRequest(
                    field.fieldName() + "不支持操作符：" + operator,
                    fieldPath + ".op",
                    field.fieldName() + "不支持操作符：" + operator);
        }
    }

    private @Nullable String convertSearchTextValue(ArchiveFieldDto field, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = StringUtils.trimToNull(value.toString());
        if (text == null) {
            return null;
        }
        return convertTextValue(field, text);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private @Nullable Object convertValue(ArchiveFieldDto field, @Nullable Object value) {
        if (value instanceof String text) {
            value = StringUtils.trimToNull(text);
        }
        if (value == null) {
            return null;
        }
        try {
            return switch (field.fieldType()) {
                case TEXT -> convertTextValue(field, value);
                case INTEGER ->
                        value instanceof Number number
                                ? number.intValue()
                                : Integer.parseInt(value.toString());
                case DECIMAL ->
                        value instanceof BigDecimal decimal
                                ? decimal
                                : new BigDecimal(value.toString());
                case DATE ->
                        value instanceof LocalDate localDate
                                ? Date.valueOf(localDate)
                                : Date.valueOf(value.toString());
                case DATETIME ->
                        value instanceof LocalDateTime localDateTime
                                ? Timestamp.valueOf(localDateTime)
                                : Timestamp.valueOf(LocalDateTime.parse(value.toString()));
            };
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw badRequest(
                    field.fieldName() + "格式不合法",
                    "dynamicFields." + field.fieldCode(),
                    field.fieldName() + "格式不合法");
        }
    }

    private String convertTextValue(ArchiveFieldDto field, Object value) {
        String text = StringUtils.trimToNull(value.toString());
        if (text == null) {
            return "";
        }
        if (field.textLength() != null && text.length() > field.textLength()) {
            String message = field.fieldName() + "长度不能超过 " + field.textLength();
            throw badRequest(message, "dynamicFields." + field.fieldCode(), message);
        }
        return text;
    }

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }
}
