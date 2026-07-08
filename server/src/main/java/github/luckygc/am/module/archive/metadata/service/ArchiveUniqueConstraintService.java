package github.luckygc.am.module.archive.metadata.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintRequest;

@Service
public class ArchiveUniqueConstraintService {

    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    private final ArchiveMapper archiveMapper;
    private final ArchiveFieldDefinitionService fieldDefinitionService;
    private final ArchiveDynamicTableService dynamicTableService;

    public ArchiveUniqueConstraintService(
            ArchiveMapper archiveMapper,
            ArchiveFieldDefinitionService fieldDefinitionService,
            ArchiveDynamicTableService dynamicTableService) {
        this.archiveMapper = archiveMapper;
        this.fieldDefinitionService = fieldDefinitionService;
        this.dynamicTableService = dynamicTableService;
    }

    List<ArchiveUniqueConstraintDto> list(Long categoryId) {
        return archiveMapper.listUniqueConstraints(categoryId).stream()
                .map(
                        row ->
                                mapUniqueConstraint(
                                        row,
                                        listUniqueConstraintFields(number(row, "id").longValue())))
                .toList();
    }

    ArchiveUniqueConstraintDto create(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            ArchiveUniqueConstraintRequest request,
            Long userId) {
        ArchiveUniqueConstraintValues values = validate(category, fields, request);
        String indexName = uniqueConstraintIndexName(category, values);
        Long id =
                archiveMapper.insertUniqueConstraint(
                        category.id(),
                        values.archiveLevel().value(),
                        values.constraintCode(),
                        values.constraintName(),
                        indexName,
                        values.enabled(),
                        userId);
        replaceUniqueConstraintFields(id, values.fieldIds());
        markUniqueConstraintFieldsSearchable(category, fields, values.fieldIds(), userId);
        ArchiveUniqueConstraintDto constraint = findRequired(id);
        syncUniqueIndex(category, constraint);
        return constraint;
    }

    ArchiveUniqueConstraintDto update(
            ArchiveCategoryDto category,
            ArchiveUniqueConstraintDto current,
            List<ArchiveFieldDto> fields,
            ArchiveUniqueConstraintRequest request,
            Long userId) {
        dynamicTableService.dropIndexIfExists(current.indexName());
        ArchiveUniqueConstraintValues values = validate(category, fields, request);
        String indexName = uniqueConstraintIndexName(category, values);
        int updated =
                archiveMapper.updateUniqueConstraint(
                        current.id(),
                        category.id(),
                        values.archiveLevel().value(),
                        values.constraintCode(),
                        values.constraintName(),
                        indexName,
                        values.enabled(),
                        userId);
        if (updated == 0) {
            throw notFound("唯一约束不存在");
        }
        replaceUniqueConstraintFields(current.id(), values.fieldIds());
        markUniqueConstraintFieldsSearchable(category, fields, values.fieldIds(), userId);
        ArchiveUniqueConstraintDto constraint = findRequired(current.id());
        syncUniqueIndex(category, constraint);
        return constraint;
    }

    void delete(ArchiveUniqueConstraintDto constraint, Long categoryId, Long userId) {
        dynamicTableService.dropIndexIfExists(constraint.indexName());
        int updated = archiveMapper.deleteUniqueConstraint(constraint.id(), categoryId, userId);
        if (updated == 0) {
            throw notFound("唯一约束不存在");
        }
    }

    @Nullable ArchiveUniqueConstraintDto find(Long id) {
        Map<String, @Nullable Object> row = archiveMapper.getUniqueConstraint(id);
        if (row == null) {
            return null;
        }
        return mapUniqueConstraint(row, listUniqueConstraintFields(id));
    }

    List<ArchiveUniqueConstraintFieldDto> listUniqueConstraintFields(Long constraintId) {
        return archiveMapper.listUniqueConstraintFields(constraintId).stream()
                .map(this::mapUniqueConstraintField)
                .toList();
    }

    void replaceUniqueConstraintFields(Long constraintId, List<Long> fieldIds) {
        archiveMapper.deleteUniqueConstraintFields(constraintId);
        for (int index = 0; index < fieldIds.size(); index++) {
            archiveMapper.insertUniqueConstraintField(constraintId, fieldIds.get(index), index + 1);
        }
    }

    void markUniqueConstraintFieldsSearchable(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            List<Long> fieldIds,
            Long userId) {
        if (fieldIds.isEmpty()) {
            return;
        }
        archiveMapper.markFieldsExactSearchable(category.id(), fieldIds, userId);
        fields.stream()
                .filter(field -> fieldIds.contains(field.id()))
                .filter(
                        field ->
                                dynamicTableService.isDynamicTableBuilt(
                                        category, field.archiveLevel(), field.fieldScope()))
                .forEach(
                        field ->
                                dynamicTableService.createExactIndex(
                                        dynamicTableService.dynamicTableName(
                                                category, field.archiveLevel(), field.fieldScope()),
                                        field.columnName()));
    }

    ArchiveUniqueConstraintValues validate(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            ArchiveUniqueConstraintRequest request) {
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
        ArchiveLevel archiveLevel =
                fieldDefinitionService.normalizeArchiveLevel(request.archiveLevel());
        fieldDefinitionService.ensureArchiveLevelAllowed(category, archiveLevel);
        Map<Long, ArchiveFieldDto> fieldsById =
                fields.stream().collect(Collectors.toMap(ArchiveFieldDto::id, field -> field));
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
        return new ArchiveUniqueConstraintValues(
                archiveLevel,
                constraintCode,
                request.constraintName().trim(),
                request.enabled() == null || request.enabled(),
                fieldIds);
    }

    private void syncUniqueIndex(
            ArchiveCategoryDto category, ArchiveUniqueConstraintDto constraint) {
        if (constraint.enabled()
                && dynamicTableService.isDynamicTableBuilt(category, constraint.archiveLevel())) {
            dynamicTableService.createUniqueIndex(
                    dynamicTableService.dynamicTableName(category, constraint.archiveLevel()),
                    constraint);
        }
    }

    private String uniqueConstraintIndexName(
            ArchiveCategoryDto category, ArchiveUniqueConstraintValues values) {
        return dynamicTableService.uniqueConstraintIndexName(
                category.categoryCode(), values.archiveLevel(), values.constraintCode());
    }

    private ArchiveUniqueConstraintDto findRequired(Long id) {
        ArchiveUniqueConstraintDto constraint = find(id);
        if (constraint == null) {
            throw notFound("唯一约束不存在");
        }
        return constraint;
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

    private void validateRequired(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw badRequest(message);
        }
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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    record ArchiveUniqueConstraintValues(
            ArchiveLevel archiveLevel,
            String constraintCode,
            String constraintName,
            boolean enabled,
            List<Long> fieldIds) {}
}
