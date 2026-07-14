package github.luckygc.am.module.archive.item.service;

import java.util.List;
import java.util.Locale;
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
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemLineTableService {

    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    private final ArchiveMapper archiveMapper;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveCategoryService archiveCategoryService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemLineTableService(
            ArchiveMapper archiveMapper,
            ArchiveMetadataService archiveMetadataService,
            ArchiveCategoryService archiveCategoryService,
            AuthorizationPermissionService permissionService) {
        this.archiveMapper = archiveMapper;
        this.archiveMetadataService = archiveMetadataService;
        this.archiveCategoryService = archiveCategoryService;
        this.permissionService = permissionService;
    }

    public List<ArchiveItemLineTableDto> listLineTables(Long categoryId, Long userId) {
        requireMetadataManage(userId);
        archiveCategoryService.getCategory(categoryId);
        return archiveMapper.listItemLineTables(categoryId).stream()
                .map(this::toLineTableDto)
                .toList();
    }

    @Transactional
    public ArchiveItemLineTableDto createLineTable(
            Long categoryId, ArchiveItemLineTableRequest request, Long userId) {
        requireMetadataManage(userId);
        ArchiveCategoryDto category = archiveCategoryService.getCategory(categoryId);
        String tableCode = requiredCode(request.tableCode(), "明细表编码不能为空");
        String tableName = StringUtils.trimToNull(request.tableName());
        if (tableName == null) {
            throw new BadRequestException("明细表名称不能为空");
        }
        String physicalName =
                StringUtils.defaultIfBlank(
                        request.physicalTableName(),
                        ArchiveDynamicTableNames.stableIdentifier(
                                "am_archive_item_line_",
                                category.categoryCode() + "_" + tableCode));
        validateIdentifier(physicalName, "动态明细表名非法");
        Long id =
                archiveMapper.insertItemLineTable(
                        categoryId,
                        tableCode,
                        tableName,
                        physicalName,
                        request.sortOrder() == null ? 0 : request.sortOrder(),
                        userId);
        return getLineTableInternal(id, userId);
    }

    public ArchiveItemLineTableDto getLineTable(Long id, Long userId) {
        return getLineTableInternal(id, userId);
    }

    private ArchiveItemLineTableDto getLineTableInternal(Long id, Long userId) {
        requireMetadataManage(userId);
        Map<String, Object> row = archiveMapper.getItemLineTable(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "明细表不存在");
        }
        ArchiveItemLineTableDto table = toLineTableDto(row);
        return new ArchiveItemLineTableDto(
                table.id(),
                table.categoryId(),
                table.tableCode(),
                table.tableName(),
                table.physicalTableName(),
                table.sortOrder(),
                table.enabled(),
                listLineFieldsInternal(table.id(), userId));
    }

    public List<ArchiveItemLineFieldDto> listLineFields(Long lineTableId, Long userId) {
        return listLineFieldsInternal(lineTableId, userId);
    }

    private List<ArchiveItemLineFieldDto> listLineFieldsInternal(Long lineTableId, Long userId) {
        requireMetadataManage(userId);
        getLineTableRow(lineTableId);
        return archiveMapper.listItemLineFields(lineTableId).stream()
                .map(this::toLineFieldDto)
                .toList();
    }

    @Transactional
    public ArchiveItemLineFieldDto createLineField(
            Long lineTableId, ArchiveItemLineFieldRequest request, Long userId) {
        requireMetadataManage(userId);
        getLineTableRow(lineTableId);
        String fieldCode = requiredCode(request.fieldCode(), "字段编码不能为空");
        String fieldName = StringUtils.trimToNull(request.fieldName());
        if (fieldName == null) {
            throw new BadRequestException("字段名称不能为空");
        }
        ArchiveFieldType fieldType =
                request.fieldType() == null ? ArchiveFieldType.TEXT : request.fieldType();
        String columnName =
                StringUtils.defaultIfBlank(
                        request.columnName(), "f_" + fieldCode.toLowerCase(Locale.ROOT));
        validateIdentifier(columnName, "字段列名非法");
        Long id =
                archiveMapper.insertItemLineField(
                        lineTableId,
                        fieldCode,
                        fieldName,
                        fieldType.value(),
                        columnName,
                        request.exactSearchable(),
                        request.sortOrder() == null ? 0 : request.sortOrder(),
                        userId);
        return listLineFieldsInternal(lineTableId, userId).stream()
                .filter(field -> field.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("明细字段创建后不可见"));
    }

    @Transactional
    public ArchiveItemLineTableDto buildLineTable(Long lineTableId, Long userId) {
        requireMetadataManage(userId);
        ArchiveItemLineTableDto table = getLineTableInternal(lineTableId, userId);
        if (table.fields().isEmpty()) {
            throw new BadRequestException("明细表没有可建表字段");
        }
        validateIdentifier(table.physicalTableName(), "动态明细表名非法");
        for (ArchiveItemLineFieldDto field : table.fields()) {
            validateIdentifier(field.columnName(), "字段列名非法");
        }
        if (archiveMapper.tableExists(table.physicalTableName()) == 0) {
            String columns =
                    table.fields().stream()
                            .map(field -> field.columnName() + " " + sqlType(field.fieldType()))
                            .reduce("", (left, right) -> left + ",\n    " + right);
            archiveMapper.executeSql(
                    """
                    create table %s
                    (
                        id bigserial primary key,
                        item_id bigint not null references am_archive_item (id),
                        line_order integer not null default 0,
                        deleted_flag boolean not null default false,
                        deleted_at timestamp,
                        deleted_by bigint,
                        created_at timestamp not null default localtimestamp,
                        updated_at timestamp not null default localtimestamp%s
                    )
                    """
                            .formatted(table.physicalTableName(), columns));
            archiveMapper.executeSql(
                    "create index %s on %s (item_id, line_order, id) where deleted_flag = false"
                            .formatted(
                                    ArchiveDynamicTableNames.stableIdentifier(
                                            "idx_am_archive_item_line_", table.physicalTableName()),
                                    table.physicalTableName()));
        } else {
            for (ArchiveItemLineFieldDto field : table.fields()) {
                if (archiveMapper.columnExists(table.physicalTableName(), field.columnName())
                        == 0) {
                    archiveMapper.executeSql(
                            "alter table %s add column %s %s"
                                    .formatted(
                                            table.physicalTableName(),
                                            field.columnName(),
                                            sqlType(field.fieldType())));
                }
            }
        }
        archiveMapper.updateItemLineTablePhysicalName(
                lineTableId, table.physicalTableName(), userId);
        return getLineTableInternal(lineTableId, userId);
    }

    private void requireMetadataManage(Long userId) {
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
    }

    private String sqlType(ArchiveFieldType fieldType) {
        return switch (fieldType) {
            case TEXT -> "text";
            case INTEGER -> "integer";
            case DECIMAL -> "numeric";
            case DATE -> "date";
            case DATETIME -> "timestamp";
        };
    }

    private Map<String, Object> getLineTableRow(Long id) {
        Map<String, Object> row = archiveMapper.getItemLineTable(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "明细表不存在");
        }
        return row;
    }

    private ArchiveItemLineTableDto toLineTableDto(Map<String, Object> row) {
        return new ArchiveItemLineTableDto(
                number(row, "id").longValue(),
                number(row, "categoryId").longValue(),
                string(row, "tableCode"),
                string(row, "tableName"),
                string(row, "physicalTableName"),
                number(row, "sortOrder").intValue(),
                bool(row, "enabled"),
                List.of());
    }

    private ArchiveItemLineFieldDto toLineFieldDto(Map<String, Object> row) {
        return new ArchiveItemLineFieldDto(
                number(row, "id").longValue(),
                number(row, "lineTableId").longValue(),
                string(row, "fieldCode"),
                string(row, "fieldName"),
                ArchiveFieldType.fromValue(string(row, "fieldType")),
                string(row, "columnName"),
                bool(row, "exactSearchable"),
                number(row, "sortOrder").intValue(),
                bool(row, "enabled"));
    }

    private String requiredCode(String value, String message) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        validateIdentifier(normalized, message);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validateIdentifier(String value, String message) {
        if (StringUtils.isBlank(value)
                || value.length() > POSTGRESQL_IDENTIFIER_LIMIT
                || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(message);
        }
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private boolean bool(Map<String, Object> row, String key) {
        return Boolean.TRUE.equals(value(row, key));
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveItemLineTableRequest(
            @Nullable String tableCode,
            @Nullable String tableName,
            @Nullable String physicalTableName,
            @Nullable Integer sortOrder) {}

    public record ArchiveItemLineFieldRequest(
            @Nullable String fieldCode,
            @Nullable String fieldName,
            @Nullable ArchiveFieldType fieldType,
            @Nullable String columnName,
            boolean exactSearchable,
            @Nullable Integer sortOrder) {}

    public record ArchiveItemLineTableDto(
            Long id,
            Long categoryId,
            String tableCode,
            String tableName,
            String physicalTableName,
            int sortOrder,
            boolean enabled,
            List<ArchiveItemLineFieldDto> fields) {}

    public record ArchiveItemLineFieldDto(
            Long id,
            Long lineTableId,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            String columnName,
            boolean exactSearchable,
            int sortOrder,
            boolean enabled) {}
}
