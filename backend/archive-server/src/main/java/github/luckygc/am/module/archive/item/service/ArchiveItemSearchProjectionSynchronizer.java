package github.luckygc.am.module.archive.item.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowRequests.ArchiveItemLineRowProjectionRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

@Service
public class ArchiveItemSearchProjectionSynchronizer {

    private static final String SEARCH_EVENT_DELETE = "DELETE";
    private static final int SEARCH_OUTBOX_DRAIN_LIMIT = 100;
    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMapper archiveMapper;

    ArchiveItemSearchProjectionSynchronizer(
            ArchiveMetadataService archiveMetadataService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveMapper archiveMapper) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveMapper = archiveMapper;
    }

    void drainOutbox() {
        for (Map<String, @Nullable Object> outbox :
                archiveMapper.listPendingSearchOutbox(SEARCH_OUTBOX_DRAIN_LIMIT)) {
            Long outboxId = number(outbox, "id").longValue();
            try {
                processOutbox(outbox);
                archiveMapper.markSearchOutboxProcessed(outboxId);
            } catch (RuntimeException exception) {
                archiveMapper.markSearchOutboxFailed(outboxId, exception.getMessage());
            }
        }
    }

    void synchronize(Long recordId) {
        Map<String, @Nullable Object> recordRow = archiveMapper.getArchiveItem(recordId);
        if (recordRow == null) {
            removeProjection(recordId);
            return;
        }
        ArchiveCategoryDto category = getCategoryByCode(string(recordRow, "categoryCode"));
        ArchiveLevel archiveLevel = ArchiveLevel.ITEM;
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            removeProjection(recordId);
            return;
        }
        refreshProjection(recordId, category, archiveLevel);
    }

    private void refreshProjection(
            Long recordId, ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        String tableName = ArchiveDynamicTableNames.tableName(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(category.id(), archiveLevel);
        Map<String, @Nullable Object> dynamicRecord =
                archiveMapper.loadDynamicRecord(tableName, recordId);
        if (dynamicRecord == null) {
            removeProjection(recordId);
            return;
        }
        replaceProjection(recordId, category, fields, dynamicFieldsByCode(dynamicRecord, fields));
    }

    private void replaceProjection(
            Long recordId,
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> dynamicFields) {
        StringBuilder searchText = new StringBuilder();
        appendFieldText(searchText, fields, dynamicFields);
        for (String lineText : itemLineTexts(recordId, category.id())) {
            if (StringUtils.isBlank(lineText)) {
                continue;
            }
            if (!searchText.isEmpty()) {
                searchText.append('\n');
            }
            searchText.append(lineText);
        }
        if (searchText.isEmpty()) {
            removeProjection(recordId);
            return;
        }
        archiveMapper.replaceSearchProjection(recordId, searchText.toString(), 1);
    }

    private void appendFieldText(
            StringBuilder searchText,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> values) {
        for (ArchiveFieldDto field : fields) {
            Object value = normalizeDynamicFieldValue(field, values.get(field.fieldCode()));
            if (value == null || StringUtils.isBlank(value.toString())) {
                continue;
            }
            if (!searchText.isEmpty()) {
                searchText.append('\n');
            }
            if (StringUtils.isNotBlank(field.fieldName())) {
                searchText.append(field.fieldName().trim()).append(' ');
            }
            searchText.append(value);
        }
    }

    private List<String> itemLineTexts(Long itemId, Long categoryId) {
        List<String> lines = new ArrayList<>();
        for (Map<String, @Nullable Object> lineTable :
                archiveMapper.listItemLineTables(categoryId)) {
            String tableName = string(lineTable, "physicalTableName");
            validateIdentifier(tableName, "动态明细表名非法");
            if (archiveMapper.tableExists(tableName) == 0) {
                continue;
            }
            List<Map<String, @Nullable Object>> fields =
                    archiveMapper.listItemLineFields(number(lineTable, "id").longValue());
            List<String> selectColumns = new ArrayList<>();
            for (Map<String, @Nullable Object> field : fields) {
                String columnName = string(field, "columnName");
                validateIdentifier(columnName, "字段列名非法");
                selectColumns.add(columnName);
            }
            if (selectColumns.stream()
                    .anyMatch(
                            columnName -> archiveMapper.columnExists(tableName, columnName) == 0)) {
                continue;
            }
            List<Map<String, @Nullable Object>> rows =
                    archiveMapper.listItemLineRowsForProjection(
                            new ArchiveItemLineRowProjectionRequest(
                                    tableName, itemId, selectColumns));
            for (Map<String, @Nullable Object> row : rows) {
                StringBuilder lineText = new StringBuilder();
                for (Map<String, @Nullable Object> field : fields) {
                    String columnName = string(field, "columnName");
                    if (StringUtils.isBlank(columnName)) {
                        continue;
                    }
                    Object value = value(row, columnName);
                    if (value == null || StringUtils.isBlank(value.toString())) {
                        continue;
                    }
                    if (!lineText.isEmpty()) {
                        lineText.append(' ');
                    }
                    if (StringUtils.isNotBlank(string(field, "fieldName"))) {
                        lineText.append(string(field, "fieldName")).append(' ');
                    }
                    lineText.append(value);
                }
                if (!lineText.isEmpty()) {
                    lines.add(lineText.toString());
                }
            }
        }
        return lines;
    }

    private void removeProjection(Long recordId) {
        archiveMapper.deleteSearchProjection(recordId);
    }

    private void processOutbox(Map<String, @Nullable Object> outbox) {
        Long recordId = number(outbox, "archiveItemId").longValue();
        String eventType = string(outbox, "eventType");
        if (SEARCH_EVENT_DELETE.equals(eventType)) {
            removeProjection(recordId);
            return;
        }
        synchronize(recordId);
    }

    private Map<String, @Nullable Object> dynamicFieldsByCode(
            Map<String, @Nullable Object> dynamicRecord, List<ArchiveFieldDto> fields) {
        Map<String, @Nullable Object> dynamicFields = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            dynamicFields.put(
                    field.fieldCode(),
                    normalizeDynamicFieldValue(field, dynamicRecord.get(field.columnName())));
        }
        return dynamicFields;
    }

    private @Nullable Object normalizeDynamicFieldValue(
            ArchiveFieldDto field, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return switch (field.fieldType()) {
            case DATE -> {
                if (value instanceof Date date) {
                    yield date.toLocalDate().toString();
                }
                if (value instanceof LocalDate localDate) {
                    yield localDate.toString();
                }
                yield value;
            }
            case DATETIME -> {
                if (value instanceof Timestamp timestamp) {
                    yield timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
                }
                if (value instanceof LocalDateTime localDateTime) {
                    yield localDateTime.format(DATE_TIME_FORMATTER);
                }
                yield value;
            }
            default -> value;
        };
    }

    private boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        String tableName = ArchiveDynamicTableNames.tableName(category, archiveLevel);
        return StringUtils.isNotBlank(tableName) && archiveMapper.tableExists(tableName) > 0;
    }

    private ArchiveCategoryDto getCategoryByCode(String categoryCode) {
        return archiveCategoryService.listCategories(null).stream()
                .filter(category -> category.categoryCode().equals(categoryCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("档案分类不存在"));
    }

    private @Nullable String string(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Number number(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    private void validateIdentifier(@Nullable String value, String message) {
        if (StringUtils.isBlank(value)
                || value.length() > POSTGRESQL_IDENTIFIER_LIMIT
                || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(message);
        }
    }
}
