package github.luckygc.am.module.archive.record;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldDto;

@Service
class ArchiveRecordSearchProjectionService {

    private static final String SEARCH_EVENT_UPSERT = "UPSERT";
    private static final String SEARCH_EVENT_DELETE = "DELETE";
    private static final int SEARCH_OUTBOX_DRAIN_LIMIT = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMapper archiveMapper;

    ArchiveRecordSearchProjectionService(
            ArchiveMetadataService archiveMetadataService, ArchiveMapper archiveMapper) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMapper = archiveMapper;
    }

    void enqueueUpsert(Long recordId) {
        archiveMapper.insertSearchOutbox(recordId, SEARCH_EVENT_UPSERT);
    }

    void drainOutbox() {
        for (Map<String, Object> outbox :
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

    void refreshFromDynamicRecord(
            Long recordId, ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        String tableName = ArchiveDynamicTableNames.tableName(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(category.id(), archiveLevel);
        Map<String, Object> dynamicRecord = archiveMapper.loadDynamicRecord(tableName, recordId);
        if (dynamicRecord == null) {
            delete(recordId);
            return;
        }
        upsert(recordId, fields, dynamicFieldsByCode(dynamicRecord, fields));
    }

    void upsert(Long recordId, List<ArchiveFieldDto> fields, Map<String, Object> dynamicFields) {
        StringBuilder searchText = new StringBuilder();
        for (ArchiveFieldDto field : fields) {
            Object value = normalizeDynamicFieldValue(field, dynamicFields.get(field.fieldCode()));
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
        if (searchText.isEmpty()) {
            delete(recordId);
            return;
        }
        archiveMapper.insertSearchProjection(recordId, searchText.toString(), 1);
    }

    void delete(Long recordId) {
        archiveMapper.deleteSearchProjection(recordId);
    }

    private void processOutbox(Map<String, Object> outbox) {
        Long recordId = number(outbox, "archiveRecordId").longValue();
        String eventType = string(outbox, "eventType");
        if (SEARCH_EVENT_DELETE.equals(eventType)) {
            delete(recordId);
            return;
        }

        Map<String, Object> recordRow = archiveMapper.getArchiveRecord(recordId);
        if (recordRow == null) {
            delete(recordId);
            return;
        }
        ArchiveCategoryDto category = getCategoryByCode(string(recordRow, "categoryCode"));
        ArchiveLevel archiveLevel = ArchiveLevel.fromValue(string(recordRow, "archiveLevel"));
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            delete(recordId);
            return;
        }
        refreshFromDynamicRecord(recordId, category, archiveLevel);
    }

    private Map<String, Object> dynamicFieldsByCode(
            Map<String, Object> dynamicRecord, List<ArchiveFieldDto> fields) {
        return fields.stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                ArchiveFieldDto::fieldCode,
                                field ->
                                        normalizeDynamicFieldValue(
                                                field, dynamicRecord.get(field.columnName())),
                                (left, right) -> right,
                                java.util.LinkedHashMap::new));
    }

    private Object normalizeDynamicFieldValue(ArchiveFieldDto field, Object value) {
        if (value == null) {
            return null;
        }
        return switch (field.fieldType()) {
            case date -> {
                if (value instanceof Date date) {
                    yield date.toLocalDate().toString();
                }
                if (value instanceof LocalDate localDate) {
                    yield localDate.toString();
                }
                yield value;
            }
            case datetime -> {
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
        return archiveMetadataService.listCategories(null).stream()
                .filter(category -> category.categoryCode().equals(categoryCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("档案分类不存在"));
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }
}
