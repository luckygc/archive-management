package github.luckygc.am.module.archive.metadata.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintFieldDto;

@Service
public class ArchiveDynamicTableService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;

    private final ArchiveMapper archiveMapper;
    private final ArchiveFieldDefinitionService fieldDefinitionService;

    public ArchiveDynamicTableService(
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
                            .map(
                                    field ->
                                            field.columnName()
                                                    + " "
                                                    + fieldDefinitionService.sqlType(field))
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

    void syncDynamicColumnAfterFieldUpdate(
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
            ensureColumn(tableName, after.columnName(), fieldDefinitionService.sqlType(after));
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
                        .formatted(
                                tableName,
                                after.columnName(),
                                fieldDefinitionService.sqlType(after)));
        if (after.exactSearchable()) {
            createExactIndex(tableName, after.columnName());
        }
    }

    boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    boolean isDynamicTableBuilt(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        return StringUtils.isNotBlank(tableName) && archiveMapper.tableExists(tableName) > 0;
    }

    String dynamicTableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return dynamicTableName(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    String dynamicTableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.tableName(category, archiveLevel, fieldScope);
    }

    void createExactIndex(String tableName, String columnName) {
        validateIdentifier(tableName, "动态表名非法");
        validateIdentifier(columnName, "字段列名非法");
        String indexName = "idx_" + tableName + "_" + columnName + "_active";
        if (indexName.length() > POSTGRESQL_IDENTIFIER_LIMIT) {
            indexName = "idx_archive_exact_" + Math.abs((tableName + "_" + columnName).hashCode());
        }
        validateIdentifier(indexName, "索引名非法");
        if (archiveMapper.indexExists(indexName) == 0) {
            archiveMapper.executeSql(
                    "create index %s on %s (%s) where deleted_flag = false"
                            .formatted(indexName, tableName, columnName));
        }
    }

    void createUniqueIndex(String tableName, ArchiveUniqueConstraintDto constraint) {
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

    void dropIndexIfExists(String indexName) {
        if (StringUtils.isBlank(indexName)) {
            return;
        }
        validateIdentifier(indexName, "索引名非法");
        archiveMapper.executeSql("drop index if exists %s".formatted(indexName));
    }

    String uniqueConstraintIndexName(
            String categoryCode, ArchiveLevel archiveLevel, String constraintCode) {
        String seed =
                categoryCode.toLowerCase(Locale.ROOT)
                        + "_"
                        + archiveLevel.value()
                        + "_"
                        + constraintCode;
        return ArchiveDynamicTableNames.stableIdentifier("uk_am_archive_constraint_", seed);
    }

    private void ensureColumn(String tableName, String columnName, String type) {
        validateIdentifier(tableName, "动态表名非法");
        validateIdentifier(columnName, "字段列名非法");
        if (archiveMapper.columnExists(tableName, columnName) == 0) {
            archiveMapper.executeSql(
                    "alter table %s add column %s %s".formatted(tableName, columnName, type));
        }
    }

    private void validateIdentifier(String value, String message) {
        if (StringUtils.isBlank(value)
                || value.length() > POSTGRESQL_IDENTIFIER_LIMIT
                || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
