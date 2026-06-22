package github.luckygc.am.module.archive;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.web.ApiBadRequestException;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveUniqueRuleDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveUniqueRuleFieldDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;

@Service
public class ArchiveRecordRoutingService {

    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_UPDATE = "UPDATE";
    private static final String AUDIT_OPERATION_DELETE = "DELETE";
    private static final String AUDIT_OPERATION_LOCK = "LOCK";
    private static final String AUDIT_OPERATION_UNLOCK = "UNLOCK";
    private static final String SEARCH_EVENT_UPSERT = "UPSERT";
    private static final String SEARCH_EVENT_DELETE = "DELETE";
    private static final int SEARCH_OUTBOX_DRAIN_LIMIT = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMapper archiveMapper;

    public ArchiveRecordRoutingService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMapper archiveMapper) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMapper = archiveMapper;
    }

    public ArchiveRecordListDto listRecords(Long categoryId, String fondsCode) {
        return listRecords(categoryId, fondsCode, null);
    }

    public ArchiveRecordListDto listRecords(Long categoryId, String fondsCode, Long userId) {
        return searchRecords(new ArchiveRecordQueryRequest(categoryId, fondsCode, null, null, null), userId);
    }

    public ArchiveRecordListDto searchRecords(ArchiveRecordQueryRequest request) {
        return searchRecords(request, null);
    }

    public ArchiveRecordListDto searchRecords(ArchiveRecordQueryRequest request, Long userId) {
        if (request == null || request.categoryId() == null) {
            return new ArchiveRecordListDto(null, List.of(), true, archiveMapper.listRecordOverview());
        }

        ArchiveCategoryDto category = archiveMetadataService.getCategory(request.categoryId());
        List<ArchiveFieldDto> fields = archiveMetadataService.listEffectiveFields(
                request.categoryId(), ArchiveLayoutSurface.TABLE, userId);
        List<ArchiveFieldDto> visibleFields = fields.stream()
                .filter(ArchiveFieldDto::listVisible)
                .sorted(java.util.Comparator
                        .comparingInt(ArchiveFieldDto::listSortOrder)
                        .thenComparing(ArchiveFieldDto::id))
                .toList();
        if (category.tableStatus() != ArchiveTableStatus.BUILT
                || StringUtils.isBlank(category.recordTableName())) {
            return new ArchiveRecordListDto(category, fields, false, List.of());
        }

        List<ArchiveSqlCondition> conditions =
                buildSearchConditions(request.categoryId(), fields, request.exactFilters(), request.filters());
        List<Long> recordIds = null;
        if (StringUtils.isNotBlank(request.keyword())) {
            recordIds = archiveMapper.searchRecordIds(request.keyword().trim())
                    .stream()
                    .map(row -> ((Number) row.get("archiveRecordId")).longValue())
                    .toList();
            if (recordIds.isEmpty()) {
                return new ArchiveRecordListDto(category, visibleFields, true, List.of());
            }
        }

        List<Map<String, Object>> rows = archiveMapper.listDynamicRecords(
                category.recordTableName(),
                selectColumns(visibleFields),
                category.categoryCode(),
                StringUtils.trimToNull(request.fondsCode()),
                conditions,
                recordIds);
        return new ArchiveRecordListDto(category, fields, true, normalizeDynamicFieldValues(rows, visibleFields));
    }

    @Transactional
    public ArchiveRecordDto createRecord(ArchiveRecordRequest request, Long userId) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (request.categoryId() == null) {
            throw badRequest("档案分类不能为空", "categoryId", "档案分类不能为空");
        }
        ArchiveCategoryDto category = archiveMetadataService.getCategory(request.categoryId());
        if (category.tableStatus() != ArchiveTableStatus.BUILT
                || StringUtils.isBlank(category.recordTableName())) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        List<ArchiveFieldDto> fields = archiveMetadataService.listEnabledFields(request.categoryId());
        Map<String, Object> dynamicFields =
                request.dynamicFields() == null ? Map.of() : request.dynamicFields();
        int archiveYear = request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, Object> convertedDynamicFields = convertDynamicFields(fields, dynamicFields);

        Long recordId = archiveMapper.insertArchiveRecord(
                category.categoryCode(),
                category.categoryName(),
                StringUtils.trimToNull(request.archiveNo()),
                StringUtils.defaultIfBlank(request.archiveStatus(), "DRAFT"),
                StringUtils.defaultIfBlank(request.processStatus(), "NONE"),
                archiveYear);
        try {
            insertDynamicRecord(category.recordTableName(), recordId, fonds.fondsCode(), fields, convertedDynamicFields);
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案记录违反唯一规则");
        }
        enqueueSearchProjection(recordId, SEARCH_EVENT_UPSERT);
        drainSearchProjectionOutbox();
        ArchiveRecordDto record = getRecord(recordId);
        insertRecordAudit(
                AUDIT_OPERATION_CREATE,
                record,
                null,
                userId);
        return record;
    }

    @Transactional
    public SearchProjectionRebuildResult rebuildSearchProjection(Long categoryId) {
        ArchiveCategoryDto category = archiveMetadataService.getCategory(categoryId);
        if (category.tableStatus() != ArchiveTableStatus.BUILT
                || StringUtils.isBlank(category.recordTableName())) {
            throw badRequest("档案分类尚未建表");
        }
        List<ArchiveFieldDto> fields = archiveMetadataService.listEnabledFields(categoryId);
        List<ArchiveFieldDto> fullTextFields = fields.stream()
                .filter(ArchiveFieldDto::fullTextSearchable)
                .toList();
        if (fullTextFields.isEmpty()) {
            return new SearchProjectionRebuildResult(categoryId, 0);
        }
        List<Map<String, Object>> rows = archiveMapper.listRecordsForSearchRebuild(
                category.recordTableName(),
                selectColumns(fullTextFields),
                category.categoryCode());
        int rebuilt = 0;
        for (Map<String, Object> row : rows) {
            enqueueSearchProjection(number(row, "id").longValue(), SEARCH_EVENT_UPSERT);
            rebuilt++;
        }
        drainSearchProjectionOutbox();
        return new SearchProjectionRebuildResult(categoryId, rebuilt);
    }

    public ArchiveRecordDetailDto getRecordDetail(Long id) {
        return getRecordDetail(id, null, ArchiveLayoutSurface.DETAIL);
    }

    public ArchiveRecordDetailDto getRecordDetail(Long id, Long userId, ArchiveLayoutSurface surface) {
        ArchiveRecordDto record = getRecord(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        List<ArchiveFieldDto> fields = archiveMetadataService.listEffectiveFields(
                category.id(), surface == null ? ArchiveLayoutSurface.DETAIL : surface, userId);
        Map<String, Object> dynamicRecord = loadDynamicRecord(category, record.id());
        return new ArchiveRecordDetailDto(record, category, fields, dynamicFieldsByCode(dynamicRecord, fields));
    }

    @Transactional
    public ArchiveRecordDetailDto updateRecord(Long id, ArchiveRecordUpdateRequest request, Long userId) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        ArchiveRecordDetailDto before = getRecordDetail(id);
        ensureRecordEditable(before.record());
        ArchiveCategoryDto category = before.category();
        if (category.tableStatus() != ArchiveTableStatus.BUILT
                || StringUtils.isBlank(category.recordTableName())) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        int archiveYear = request.archiveYear() == null ? before.record().archiveYear() : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, Object> requestDynamicFields = request.dynamicFields() == null
                ? before.dynamicFields()
                : request.dynamicFields();
        Map<String, Object> convertedDynamicFields = convertDynamicFields(before.fields(), requestDynamicFields);
        int updated = archiveMapper.updateArchiveRecord(
                id,
                StringUtils.trimToNull(request.archiveNo()),
                StringUtils.defaultIfBlank(request.archiveStatus(), before.record().archiveStatus()),
                StringUtils.defaultIfBlank(request.processStatus(), before.record().processStatus()),
                archiveYear);
        if (updated == 0) {
            throw badRequest("档案记录已锁定，不能修改");
        }
        try {
            archiveMapper.updateDynamicRecord(
                    category.recordTableName(),
                    id,
                    dynamicAssignments(fonds.fondsCode(), before.fields(), convertedDynamicFields));
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案记录违反唯一规则");
        }
        enqueueSearchProjection(id, SEARCH_EVENT_UPSERT);
        drainSearchProjectionOutbox();
        ArchiveRecordDetailDto after = getRecordDetail(id, userId, ArchiveLayoutSurface.EDIT);
        insertRecordAudit(
                AUDIT_OPERATION_UPDATE,
                after.record(),
                null,
                userId);
        return after;
    }

    @Transactional
    public void deleteRecord(Long id, Long userId, DeleteRecordRequest request) {
        ArchiveRecordDto record = getRecord(id);
        ensureRecordEditable(record);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        insertRecordAudit(
                AUDIT_OPERATION_DELETE,
                record,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        if (category.tableStatus() == ArchiveTableStatus.BUILT
                && StringUtils.isNotBlank(category.recordTableName())) {
            archiveMapper.markDynamicRecordDeleted(category.recordTableName(), id);
        }
        int updated = archiveMapper.markArchiveRecordDeleted(id);
        if (updated == 0) {
            throw badRequest("档案记录已锁定，不能删除");
        }
        enqueueSearchProjection(id, SEARCH_EVENT_DELETE);
        drainSearchProjectionOutbox();
    }

    @Transactional
    public ArchiveRecordDto lockRecord(Long id, Long userId, LockRecordRequest request) {
        if (id == null || id <= 0) {
            throw badRequest("档案记录 ID 不合法");
        }
        int updated = archiveMapper.lockArchiveRecord(
                id,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案记录不存在");
        }
        ArchiveRecordDto after = getRecord(id);
        insertRecordAudit(
                AUDIT_OPERATION_LOCK,
                after,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        return after;
    }

    @Transactional
    public ArchiveRecordDto unlockRecord(Long id, Long userId) {
        if (id == null || id <= 0) {
            throw badRequest("档案记录 ID 不合法");
        }
        int updated = archiveMapper.unlockArchiveRecord(id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案记录不存在");
        }
        ArchiveRecordDto after = getRecord(id);
        insertRecordAudit(
                AUDIT_OPERATION_UNLOCK,
                after,
                null,
                userId);
        return after;
    }

    public ArchiveRecordDto getRecord(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("档案记录 ID 不合法");
        }
        Map<String, Object> row = archiveMapper.getArchiveRecord(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案记录不存在");
        }
        String categoryCode = string(row, "categoryCode");
        ArchiveCategoryDto category = getCategoryByCode(categoryCode);
        String fondsCode = null;
        if (category.tableStatus() == ArchiveTableStatus.BUILT
                && StringUtils.isNotBlank(category.recordTableName())) {
            Map<String, Object> dynamicRecord = archiveMapper.loadDynamicRecord(category.recordTableName(), id);
            if (dynamicRecord != null) {
                fondsCode = string(dynamicRecord, "fonds_code");
            }
        }
        return new ArchiveRecordDto(
                number(row, "id").longValue(),
                fondsCode,
                categoryCode,
                string(row, "categoryName"),
                string(row, "archiveNo"),
                string(row, "archiveStatus"),
                string(row, "processStatus"),
                number(row, "archiveYear").intValue(),
                bool(row, "lockedFlag"),
                string(row, "lockReason"),
                longOrNull(row, "lockedBy"),
                dateTime(row, "lockedAt"));
    }

    private Map<String, Object> loadDynamicRecord(ArchiveCategoryDto category, Long id) {
        if (category.tableStatus() != ArchiveTableStatus.BUILT
                || StringUtils.isBlank(category.recordTableName())) {
            return Map.of();
        }
        Map<String, Object> dynamicRecord = archiveMapper.loadDynamicRecord(category.recordTableName(), id);
        return dynamicRecord == null ? Map.of() : dynamicRecord;
    }

    private Map<String, Object> dynamicFieldsByCode(
            Map<String, Object> dynamicRecord, List<ArchiveFieldDto> fields) {
        Map<String, Object> dynamicFields = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            dynamicFields.put(
                    field.fieldCode(),
                    normalizeDynamicFieldValue(field, dynamicRecord.get(field.columnName())));
        }
        return dynamicFields;
    }

    public void ensureRecordEditable(Long id) {
        ensureRecordEditable(getRecord(id));
    }

    private void ensureRecordEditable(ArchiveRecordDto record) {
        if (record.lockedFlag()) {
            throw badRequest("档案记录已锁定，不能修改");
        }
    }

    private ArchiveCategoryDto getCategoryByCode(String categoryCode) {
        return archiveMetadataService.listCategories(null).stream()
                .filter(category -> category.categoryCode().equals(categoryCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案分类不存在"));
    }

    private List<ArchiveSqlCondition> buildSearchConditions(
            Long categoryId,
            List<ArchiveFieldDto> fields,
            Map<String, Object> exactFilters,
            List<ArchiveRecordFieldFilter> filters) {
        Map<String, ArchiveFieldDto> fieldsByCode = fields.stream()
                .collect(java.util.stream.Collectors.toMap(ArchiveFieldDto::fieldCode, field -> field));
        List<String> uniqueFieldCodes = archiveMetadataService.listUniqueRules(categoryId).stream()
                .filter(ArchiveUniqueRuleDto::enabled)
                .flatMap(rule -> rule.fields().stream())
                .map(ArchiveUniqueRuleFieldDto::fieldCode)
                .distinct()
                .toList();
        List<ArchiveSqlCondition> conditions = new ArrayList<>();
        if (exactFilters != null) {
            for (Map.Entry<String, Object> entry : exactFilters.entrySet()) {
                ArchiveFieldDto field = fieldsByCode.get(entry.getKey());
                validateSearchableField(field, entry.getKey(), uniqueFieldCodes);
                Object value = convertValue(field, entry.getValue());
                if (value != null) {
                    conditions.add(new ArchiveSqlCondition(field.columnName(), "EQ", value));
                }
            }
        }
        if (filters != null) {
            for (ArchiveRecordFieldFilter filter : filters) {
                if (filter == null || StringUtils.isBlank(filter.fieldCode())) {
                    continue;
                }
                ArchiveFieldDto field = fieldsByCode.get(filter.fieldCode());
                validateSearchableField(field, filter.fieldCode(), uniqueFieldCodes);
                conditions.addAll(toSearchConditions(field, filter));
            }
        }
        return conditions;
    }

    private void validateSearchableField(
            ArchiveFieldDto field, String fieldCode, List<String> uniqueFieldCodes) {
        if (field == null || (!field.exactSearchable() && !uniqueFieldCodes.contains(field.fieldCode()))) {
            throw badRequest("字段不允许作为筛选条件：" + fieldCode);
        }
    }

    private List<ArchiveSqlCondition> toSearchConditions(
            ArchiveFieldDto field, ArchiveRecordFieldFilter filter) {
        List<ArchiveSqlCondition> conditions = new ArrayList<>();
        if (field.fieldType() == ArchiveFieldType.TEXT) {
            Object value = convertValue(field, filter.value());
            if (value != null) {
                conditions.add(new ArchiveSqlCondition(field.columnName(), "EQ", value));
            }
            return conditions;
        }

        Object exactValue = convertValue(field, filter.value());
        if (exactValue != null) {
            conditions.add(new ArchiveSqlCondition(field.columnName(), "EQ", exactValue));
            return conditions;
        }

        Object startValue = convertValue(field, filter.startValue());
        if (startValue != null) {
            conditions.add(new ArchiveSqlCondition(field.columnName(), "GTE", startValue));
        }
        Object endValue = convertValue(field, filter.endValue());
        if (endValue != null) {
            conditions.add(new ArchiveSqlCondition(field.columnName(), "LTE", endValue));
        }
        return conditions;
    }

    private String selectColumns(List<ArchiveFieldDto> fields) {
        if (fields.isEmpty()) {
            return "";
        }
        StringBuilder columns = new StringBuilder();
        for (ArchiveFieldDto field : fields) {
            columns.append(", d.")
                    .append(field.columnName())
                    .append(" as ")
                    .append(field.columnName());
        }
        return columns.toString();
    }

    private List<Map<String, Object>> normalizeDynamicFieldValues(
            List<Map<String, Object>> rows, List<ArchiveFieldDto> fields) {
        if (rows.isEmpty() || fields.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> {
                    Map<String, Object> normalized = new LinkedHashMap<>(row);
                    for (ArchiveFieldDto field : fields) {
                        Object value = normalized.get(field.columnName());
                        normalized.put(field.columnName(), normalizeDynamicFieldValue(field, value));
                    }
                    return normalized;
                })
                .toList();
    }

    private Object normalizeDynamicFieldValue(ArchiveFieldDto field, Object value) {
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

    private void insertDynamicRecord(
            String tableName,
            Long recordId,
            String fondsCode,
            List<ArchiveFieldDto> fields,
            Map<String, Object> convertedDynamicFields) {
        StringBuilder columns = new StringBuilder("id, fonds_code");
        List<Object> values = new ArrayList<>();
        values.add(recordId);
        values.add(fondsCode);
        for (ArchiveFieldDto field : fields) {
            columns.append(", ").append(field.columnName());
            values.add(convertedDynamicFields.get(field.fieldCode()));
        }
        archiveMapper.insertDynamicRecord(tableName, columns.toString(), values);
    }

    private List<ArchiveSqlAssignment> dynamicAssignments(
            String fondsCode,
            List<ArchiveFieldDto> fields,
            Map<String, Object> convertedDynamicFields) {
        List<ArchiveSqlAssignment> assignments = new ArrayList<>();
        assignments.add(new ArchiveSqlAssignment("fonds_code", fondsCode));
        for (ArchiveFieldDto field : fields) {
            assignments.add(new ArchiveSqlAssignment(field.columnName(), convertedDynamicFields.get(field.fieldCode())));
        }
        return assignments;
    }

    private void enqueueSearchProjection(Long recordId, String eventType) {
        archiveMapper.insertSearchOutbox(recordId, eventType);
    }

    private void drainSearchProjectionOutbox() {
        for (Map<String, Object> outbox : archiveMapper.listPendingSearchOutbox(SEARCH_OUTBOX_DRAIN_LIMIT)) {
            Long outboxId = number(outbox, "id").longValue();
            try {
                processSearchProjectionOutbox(outbox);
                archiveMapper.markSearchOutboxProcessed(outboxId);
            } catch (RuntimeException exception) {
                archiveMapper.markSearchOutboxFailed(outboxId, exception.getMessage());
            }
        }
    }

    private void processSearchProjectionOutbox(Map<String, Object> outbox) {
        Long recordId = number(outbox, "archiveRecordId").longValue();
        String eventType = string(outbox, "eventType");
        if (SEARCH_EVENT_DELETE.equals(eventType)) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }

        Map<String, Object> recordRow = archiveMapper.getArchiveRecord(recordId);
        if (recordRow == null) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        ArchiveCategoryDto category = getCategoryByCode(string(recordRow, "categoryCode"));
        if (category.tableStatus() != ArchiveTableStatus.BUILT
                || StringUtils.isBlank(category.recordTableName())) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        List<ArchiveFieldDto> fields = archiveMetadataService.listEnabledFields(category.id());
        Map<String, Object> dynamicRecord = archiveMapper.loadDynamicRecord(category.recordTableName(), recordId);
        if (dynamicRecord == null) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        upsertSearchProjection(recordId, fields, dynamicFieldsByCode(dynamicRecord, fields));
    }

    private void upsertSearchProjection(
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, Object> dynamicFields) {
        boolean hasFullTextField = false;
        StringBuilder searchText = new StringBuilder();
        for (ArchiveFieldDto field : fields) {
            if (!field.fullTextSearchable()) {
                continue;
            }
            hasFullTextField = true;
            Object value = dynamicFields.get(field.fieldCode());
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                if (!searchText.isEmpty()) {
                    searchText.append('\n');
                }
                searchText.append(value);
            }
        }
        if (!hasFullTextField) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        archiveMapper.insertSearchProjection(
                recordId,
                searchText.toString(),
                1);
    }

    private void validateArchiveYear(int archiveYear) {
        int nextYear = Year.now().getValue() + 1;
        if (archiveYear < 1 || archiveYear > nextYear) {
            throw badRequest(
                    "年度必须在 1 到 " + nextYear + " 之间",
                    "archiveYear",
                    "年度必须在 1 到 " + nextYear + " 之间");
        }
    }

    private Map<String, Object> convertDynamicFields(
            List<ArchiveFieldDto> fields, Map<String, Object> dynamicFields) {
        Map<String, ArchiveFieldDto> fieldsByCode = fields.stream()
                .collect(java.util.stream.Collectors.toMap(ArchiveFieldDto::fieldCode, field -> field));
        for (String fieldCode : dynamicFields.keySet()) {
            if (!fieldsByCode.containsKey(fieldCode)) {
                throw badRequest("动态字段不存在：" + fieldCode, "dynamicFields." + fieldCode, "动态字段不存在");
            }
        }

        Map<String, Object> converted = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            converted.put(field.fieldCode(), convertValue(field, dynamicFields.get(field.fieldCode())));
        }
        return converted;
    }

    private Object convertValue(ArchiveFieldDto field, Object value) {
        if (value == null || (value instanceof String text && StringUtils.isBlank(text))) {
            return null;
        }
        try {
            return switch (field.fieldType()) {
                case TEXT -> convertTextValue(field, value);
                case INTEGER -> value instanceof Number number
                        ? number.intValue()
                        : Integer.valueOf(value.toString());
                case DECIMAL -> value instanceof BigDecimal decimal
                        ? decimal
                        : new BigDecimal(value.toString());
                case DATE -> value instanceof LocalDate localDate
                        ? Date.valueOf(localDate)
                        : Date.valueOf(value.toString());
                case DATETIME -> value instanceof LocalDateTime localDateTime
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
        String text = value.toString();
        if (field.textLength() != null && text.length() > field.textLength()) {
            throw badRequest(
                    field.fieldName() + "长度不能超过 " + field.textLength(),
                    "dynamicFields." + field.fieldCode(),
                    field.fieldName() + "长度不能超过 " + field.textLength());
        }
        return text;
    }

    private void insertRecordAudit(
            String operationType,
            ArchiveRecordDto record,
            String operationReason,
            Long operatedBy) {
        archiveMapper.insertRecordAudit(
                "am_archive_record",
                record.id(),
                record.id(),
                record.fondsCode(),
                record.categoryCode(),
                operationType,
                operationReason,
                operatedBy);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException badRequest(String message, String field, String description) {
        return new ApiBadRequestException(message, field, description);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    public record ArchiveRecordQueryRequest(
            Long categoryId,
            String fondsCode,
            String keyword,
            Map<String, Object> exactFilters,
            List<ArchiveRecordFieldFilter> filters) {}

    public record ArchiveRecordFieldFilter(
            String fieldCode,
            Object value,
            Object startValue,
            Object endValue) {}

    public record ArchiveRecordRequest(
            Long categoryId,
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String archiveStatus,
            String processStatus,
            Map<String, Object> dynamicFields) {}

    public record ArchiveRecordUpdateRequest(
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String archiveStatus,
            String processStatus,
            Map<String, Object> dynamicFields) {}

    public record DeleteRecordRequest(String reason) {}

    public record LockRecordRequest(String reason) {}

    public record ArchiveRecordDto(
            Long id,
            String fondsCode,
            String categoryCode,
            String categoryName,
            String archiveNo,
            String archiveStatus,
            String processStatus,
            int archiveYear,
            boolean lockedFlag,
            String lockReason,
            Long lockedBy,
            LocalDateTime lockedAt) {}

    public record ArchiveRecordListDto(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            boolean tableBuilt,
            List<Map<String, Object>> rows) {}

    public record ArchiveRecordDetailDto(
            ArchiveRecordDto record,
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            Map<String, Object> dynamicFields) {}

    public record SearchProjectionRebuildResult(Long categoryId, int rebuiltCount) {}
}
