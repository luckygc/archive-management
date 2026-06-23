package github.luckygc.am.module.archive.record;

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

import github.luckygc.am.common.api.LongStringSerializer;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintFieldDto;

import tools.jackson.databind.annotation.JsonSerialize;

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
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMapper archiveMapper;

    public ArchiveRecordRoutingService(
            ArchiveMetadataService archiveMetadataService, ArchiveMapper archiveMapper) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMapper = archiveMapper;
    }

    public ArchiveRecordListDto listRecords(Long categoryId, String fondsCode) {
        return listRecords(categoryId, ArchiveLevel.item, fondsCode, null);
    }

    public ArchiveRecordListDto listRecords(Long categoryId, String fondsCode, Long userId) {
        return listRecords(categoryId, ArchiveLevel.item, fondsCode, userId);
    }

    public ArchiveRecordListDto listRecords(
            Long categoryId, ArchiveLevel archiveLevel, String fondsCode, Long userId) {
        return searchRecords(
                new ArchiveRecordQueryRequest(
                        categoryId, archiveLevel, fondsCode, null, null, null),
                userId);
    }

    public ArchiveRecordListDto searchRecords(ArchiveRecordQueryRequest request) {
        return searchRecords(request, null);
    }

    public ArchiveRecordListDto searchRecords(ArchiveRecordQueryRequest request, Long userId) {
        if (request == null || request.categoryId() == null) {
            return new ArchiveRecordListDto(
                    null,
                    List.of(),
                    true,
                    normalizeRecordRowIds(archiveMapper.listRecordOverview()));
        }

        ArchiveCategoryDto category = archiveMetadataService.getCategory(request.categoryId());
        ArchiveLevel archiveLevel = normalizeArchiveLevel(request.archiveLevel());
        ensureArchiveLevelAllowed(category, archiveLevel);
        String tableName = dynamicTableName(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEffectiveFields(
                        request.categoryId(), archiveLevel, ArchiveLayoutSurface.table, userId);
        List<ArchiveFieldDto> visibleFields =
                fields.stream()
                        .filter(ArchiveFieldDto::listVisible)
                        .sorted(
                                java.util.Comparator.comparingInt(ArchiveFieldDto::listSortOrder)
                                        .thenComparing(ArchiveFieldDto::id))
                        .toList();
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            return new ArchiveRecordListDto(category, fields, false, List.of());
        }

        List<ArchiveSqlCondition> conditions =
                buildSearchConditions(
                        request.categoryId(),
                        archiveLevel,
                        fields,
                        request.exactFilters(),
                        request.filters());
        List<Long> recordIds = null;
        if (StringUtils.isNotBlank(request.keyword())) {
            recordIds =
                    archiveMapper.searchRecordIds(request.keyword().trim()).stream()
                            .map(row -> number(row, "archiveRecordId").longValue())
                            .toList();
            if (recordIds.isEmpty()) {
                return new ArchiveRecordListDto(category, visibleFields, true, List.of());
            }
        }

        List<Map<String, Object>> rows =
                archiveMapper.listDynamicRecords(
                        tableName,
                        selectColumns(visibleFields),
                        archiveLevel.value(),
                        StringUtils.trimToNull(request.fondsCode()),
                        conditions,
                        recordIds);
        return new ArchiveRecordListDto(
                category, fields, true, normalizeDynamicFieldValues(rows, visibleFields));
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
        ArchiveLevel archiveLevel = normalizeArchiveLevel(request.archiveLevel());
        ensureArchiveLevelAllowed(category, archiveLevel);
        String tableName = dynamicTableName(category, archiveLevel);
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        Long parentId =
                validateParentForWrite(
                        archiveLevel,
                        request.parentId(),
                        category.categoryCode(),
                        fonds.fondsCode());
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(request.categoryId(), archiveLevel);
        Map<String, Object> dynamicFields =
                request.dynamicFields() == null ? Map.of() : request.dynamicFields();
        int archiveYear =
                request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, Object> convertedDynamicFields = convertDynamicFields(fields, dynamicFields);

        Long recordId =
                archiveMapper.insertArchiveRecord(
                        archiveLevel.value(),
                        parentId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        category.categoryCode(),
                        category.categoryName(),
                        StringUtils.trimToNull(request.archiveNo()),
                        StringUtils.defaultIfBlank(request.electronicStatus(), "DRAFT"),
                        archiveYear,
                        userId);
        try {
            insertDynamicRecord(
                    tableName, recordId, fonds.fondsCode(), fields, convertedDynamicFields);
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案记录违反唯一约束");
        }
        upsertPhysicalObjectIfPresent(recordId, request.physicalObject(), userId);
        upsertSearchProjection(recordId, fields, convertedDynamicFields);
        ArchiveRecordDto record = getRecord(recordId);
        insertRecordAudit(AUDIT_OPERATION_CREATE, record, null, userId);
        return record;
    }

    @Transactional
    public SearchProjectionRebuildResult rebuildSearchProjection(Long categoryId) {
        ArchiveCategoryDto category = archiveMetadataService.getCategory(categoryId);
        int rebuilt = 0;
        for (ArchiveLevel archiveLevel : ArchiveLevel.orderedValues()) {
            if (archiveLevel == ArchiveLevel.volume
                    && category.managementMode() != ArchiveManagementMode.volume_item) {
                continue;
            }
            if (!isDynamicTableBuilt(category, archiveLevel)) {
                continue;
            }
            String tableName = dynamicTableName(category, archiveLevel);
            List<ArchiveFieldDto> fields =
                    archiveMetadataService.listEnabledFields(categoryId, archiveLevel);
            if (fields.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> rows =
                    archiveMapper.listRecordsForSearchRebuild(
                            tableName, selectColumns(List.of()), archiveLevel.value());
            for (Map<String, Object> row : rows) {
                enqueueSearchProjection(number(row, "id").longValue(), SEARCH_EVENT_UPSERT);
                rebuilt++;
            }
        }
        drainSearchProjectionOutbox();
        return new SearchProjectionRebuildResult(categoryId, rebuilt);
    }

    public ArchiveRecordDetailDto getRecordDetail(Long id) {
        return getRecordDetail(id, null, ArchiveLayoutSurface.detail);
    }

    public ArchiveRecordDetailDto getRecordDetail(
            Long id, Long userId, ArchiveLayoutSurface surface) {
        ArchiveRecordDto record = getRecord(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEffectiveFields(
                        category.id(),
                        record.archiveLevel(),
                        surface == null ? ArchiveLayoutSurface.detail : surface,
                        userId);
        Map<String, Object> dynamicRecord = loadDynamicRecord(category, record.id());
        return new ArchiveRecordDetailDto(
                record,
                category,
                fields,
                dynamicFieldsByCode(dynamicRecord, fields),
                getPhysicalObject(record.id()));
    }

    @Transactional
    public ArchiveRecordDetailDto updateRecord(
            Long id, ArchiveRecordUpdateRequest request, Long userId) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        ArchiveRecordDetailDto before = getRecordDetail(id);
        ensureRecordEditable(before.record());
        ArchiveCategoryDto category = before.category();
        String tableName = dynamicTableName(category, before.record().archiveLevel());
        if (!isDynamicTableBuilt(category, before.record().archiveLevel())) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        Long parentId =
                validateParentForWrite(
                        before.record().archiveLevel(),
                        request.parentId() == null
                                ? before.record().parentId()
                                : request.parentId(),
                        before.record().categoryCode(),
                        fonds.fondsCode());
        int archiveYear =
                request.archiveYear() == null
                        ? before.record().archiveYear()
                        : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, Object> requestDynamicFields =
                request.dynamicFields() == null ? before.dynamicFields() : request.dynamicFields();
        Map<String, Object> convertedDynamicFields =
                convertDynamicFields(before.fields(), requestDynamicFields);
        int updated =
                archiveMapper.updateArchiveRecord(
                        id,
                        parentId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        StringUtils.trimToNull(request.archiveNo()),
                        StringUtils.defaultIfBlank(
                                request.electronicStatus(), before.record().electronicStatus()),
                        archiveYear,
                        userId);
        if (updated == 0) {
            throw badRequest("档案记录已锁定，不能修改");
        }
        try {
            archiveMapper.updateDynamicRecord(
                    tableName,
                    id,
                    dynamicAssignments(fonds.fondsCode(), before.fields(), convertedDynamicFields));
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案记录违反唯一约束");
        }
        upsertPhysicalObjectIfPresent(id, request.physicalObject(), userId);
        refreshSearchProjectionFromDynamicRecord(id, category, before.record().archiveLevel());
        ArchiveRecordDetailDto after = getRecordDetail(id, userId, ArchiveLayoutSurface.edit);
        insertRecordAudit(AUDIT_OPERATION_UPDATE, after.record(), null, userId);
        return after;
    }

    @Transactional
    public void deleteRecord(Long id, Long userId, DeleteRecordRequest request) {
        ArchiveRecordDto record = getRecord(id);
        ensureRecordEditable(record);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        String tableName = dynamicTableName(category, record.archiveLevel());
        insertRecordAudit(
                AUDIT_OPERATION_DELETE,
                record,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        if (isDynamicTableBuilt(category, record.archiveLevel())) {
            archiveMapper.markDynamicRecordDeleted(tableName, id);
        }
        int updated = archiveMapper.markArchiveRecordDeleted(id, userId);
        if (updated == 0) {
            throw badRequest("档案记录已锁定，不能删除");
        }
        archiveMapper.deleteSearchProjection(id);
    }

    @Transactional
    public ArchiveRecordDto lockRecord(Long id, Long userId, LockRecordRequest request) {
        if (id == null || id <= 0) {
            throw badRequest("档案记录 ID 不合法");
        }
        int updated =
                archiveMapper.lockArchiveRecord(
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
        int updated = archiveMapper.unlockArchiveRecord(id, userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案记录不存在");
        }
        ArchiveRecordDto after = getRecord(id);
        insertRecordAudit(AUDIT_OPERATION_UNLOCK, after, null, userId);
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
        return new ArchiveRecordDto(
                number(row, "id").longValue(),
                ArchiveLevel.fromValue(string(row, "archiveLevel")),
                longOrNull(row, "parentId"),
                string(row, "fondsCode"),
                string(row, "fondsName"),
                string(row, "categoryCode"),
                string(row, "categoryName"),
                string(row, "archiveNo"),
                string(row, "electronicStatus"),
                number(row, "archiveYear").intValue(),
                bool(row, "lockedFlag"),
                string(row, "lockReason"),
                longOrNull(row, "lockedBy"),
                dateTime(row, "lockedAt"));
    }

    private ArchivePhysicalObjectDto getPhysicalObject(Long recordId) {
        Map<String, Object> row = archiveMapper.getPhysicalObjectByRecordId(recordId);
        if (row == null) {
            return null;
        }
        return new ArchivePhysicalObjectDto(
                number(row, "id").longValue(),
                number(row, "archiveRecordId").longValue(),
                string(row, "physicalStatus"),
                string(row, "boxNo"),
                string(row, "locationNo"),
                string(row, "barcode"),
                string(row, "remark"));
    }

    private void upsertPhysicalObjectIfPresent(
            Long recordId, ArchivePhysicalObjectRequest request, Long userId) {
        if (request == null) {
            return;
        }
        boolean hasPhysicalValue =
                StringUtils.isNotBlank(request.boxNo())
                        || StringUtils.isNotBlank(request.locationNo())
                        || StringUtils.isNotBlank(request.barcode())
                        || StringUtils.isNotBlank(request.remark())
                        || !StringUtils.equals(
                                StringUtils.defaultIfBlank(request.physicalStatus(), "NONE"),
                                "NONE");
        if (!hasPhysicalValue && archiveMapper.getPhysicalObjectByRecordId(recordId) == null) {
            return;
        }
        archiveMapper.upsertPhysicalObject(
                recordId,
                StringUtils.defaultIfBlank(request.physicalStatus(), "NONE"),
                StringUtils.trimToNull(request.boxNo()),
                StringUtils.trimToNull(request.locationNo()),
                StringUtils.trimToNull(request.barcode()),
                StringUtils.trimToNull(request.remark()),
                userId);
    }

    private Map<String, Object> loadDynamicRecord(ArchiveCategoryDto category, Long id) {
        ArchiveRecordDto record = getRecord(id);
        String tableName = dynamicTableName(category, record.archiveLevel());
        if (!isDynamicTableBuilt(category, record.archiveLevel())) {
            return Map.of();
        }
        Map<String, Object> dynamicRecord = archiveMapper.loadDynamicRecord(tableName, id);
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

    private String dynamicTableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        String tableName =
                normalizedLevel == ArchiveLevel.volume
                        ? category.volumeTableName()
                        : category.itemTableName();
        if (StringUtils.isNotBlank(tableName)) {
            return tableName;
        }
        return "am_archive_record_" + normalizedLevel.value().toLowerCase() + "_" + category.id();
    }

    private ArchiveLevel normalizeArchiveLevel(ArchiveLevel archiveLevel) {
        return archiveLevel == null ? ArchiveLevel.item : archiveLevel;
    }

    private void ensureArchiveLevelAllowed(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        ArchiveLevel normalizedLevel = normalizeArchiveLevel(archiveLevel);
        if (normalizedLevel == ArchiveLevel.volume
                && category.managementMode() != ArchiveManagementMode.volume_item) {
            throw badRequest("该分类未启用案卷管理");
        }
    }

    private boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        String tableName = dynamicTableName(category, archiveLevel);
        return StringUtils.isNotBlank(tableName) && archiveMapper.tableExists(tableName) > 0;
    }

    private Long validateParentForWrite(
            ArchiveLevel archiveLevel, Long parentId, String categoryCode, String fondsCode) {
        if (archiveLevel == ArchiveLevel.volume) {
            if (parentId != null) {
                throw badRequest("案卷不能设置父记录");
            }
            return null;
        }
        if (parentId == null) {
            return null;
        }
        ArchiveRecordDto parent = getRecord(parentId);
        if (parent.archiveLevel() != ArchiveLevel.volume) {
            throw badRequest("卷内条目只能挂接到案卷");
        }
        if (parent.lockedFlag()) {
            throw badRequest("案卷已锁定，不能调整卷内条目");
        }
        if (!parent.categoryCode().equals(categoryCode) || !parent.fondsCode().equals(fondsCode)) {
            throw badRequest("卷内条目和案卷必须属于同一全宗和分类");
        }
        return parentId;
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
            ArchiveLevel archiveLevel,
            List<ArchiveFieldDto> fields,
            Map<String, Object> exactFilters,
            List<ArchiveRecordFieldFilter> filters) {
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
        if (field == null
                || (!field.exactSearchable() && !uniqueFieldCodes.contains(field.fieldCode()))) {
            throw badRequest("字段不允许作为筛选条件：" + fieldCode);
        }
    }

    private List<ArchiveSqlCondition> toSearchConditions(
            ArchiveFieldDto field, ArchiveRecordFieldFilter filter) {
        List<ArchiveSqlCondition> conditions = new ArrayList<>();
        if (field.fieldType() == ArchiveFieldType.text) {
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
            columns.append(", d.").append(field.columnName());
        }
        return columns.toString();
    }

    private List<Map<String, Object>> normalizeDynamicFieldValues(
            List<Map<String, Object>> rows, List<ArchiveFieldDto> fields) {
        if (rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(
                        row -> {
                            Map<String, Object> normalized = normalizeRecordRowIds(row);
                            for (ArchiveFieldDto field : fields) {
                                Object value = normalized.get(field.columnName());
                                normalized.put(
                                        field.columnName(),
                                        normalizeDynamicFieldValue(field, value));
                            }
                            return normalized;
                        })
                .toList();
    }

    private List<Map<String, Object>> normalizeRecordRowIds(List<Map<String, Object>> rows) {
        return rows.stream().map(this::normalizeRecordRowIds).toList();
    }

    private Map<String, Object> normalizeRecordRowIds(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        stringifyIdValue(normalized, "id");
        stringifyIdValue(normalized, "parentId");
        stringifyIdValue(normalized, "lockedBy");
        return normalized;
    }

    private void stringifyIdValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            row.put(key, number.toString());
        }
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
            assignments.add(
                    new ArchiveSqlAssignment(
                            field.columnName(), convertedDynamicFields.get(field.fieldCode())));
        }
        return assignments;
    }

    private void enqueueSearchProjection(Long recordId, String eventType) {
        archiveMapper.insertSearchOutbox(recordId, eventType);
    }

    private void drainSearchProjectionOutbox() {
        for (Map<String, Object> outbox :
                archiveMapper.listPendingSearchOutbox(SEARCH_OUTBOX_DRAIN_LIMIT)) {
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
        ArchiveLevel archiveLevel = ArchiveLevel.fromValue(string(recordRow, "archiveLevel"));
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        refreshSearchProjectionFromDynamicRecord(recordId, category, archiveLevel);
    }

    private void refreshSearchProjectionFromDynamicRecord(
            Long recordId, ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        String tableName = dynamicTableName(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(category.id(), archiveLevel);
        Map<String, Object> dynamicRecord = archiveMapper.loadDynamicRecord(tableName, recordId);
        if (dynamicRecord == null) {
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        upsertSearchProjection(recordId, fields, dynamicFieldsByCode(dynamicRecord, fields));
    }

    private void upsertSearchProjection(
            Long recordId, List<ArchiveFieldDto> fields, Map<String, Object> dynamicFields) {
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
            archiveMapper.deleteSearchProjection(recordId);
            return;
        }
        archiveMapper.insertSearchProjection(recordId, searchText.toString(), 1);
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
        Map<String, ArchiveFieldDto> fieldsByCode =
                fields.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldDto::fieldCode, field -> field));
        for (String fieldCode : dynamicFields.keySet()) {
            if (!fieldsByCode.containsKey(fieldCode)) {
                throw badRequest("动态字段不存在：" + fieldCode, "dynamicFields." + fieldCode, "动态字段不存在");
            }
        }

        Map<String, Object> converted = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            converted.put(
                    field.fieldCode(), convertValue(field, dynamicFields.get(field.fieldCode())));
        }
        return converted;
    }

    private Object convertValue(ArchiveFieldDto field, Object value) {
        if (value == null || (value instanceof String text && StringUtils.isBlank(text))) {
            return null;
        }
        try {
            return switch (field.fieldType()) {
                case text -> convertTextValue(field, value);
                case integer ->
                        value instanceof Number number
                                ? number.intValue()
                                : Integer.valueOf(value.toString());
                case decimal ->
                        value instanceof BigDecimal decimal
                                ? decimal
                                : new BigDecimal(value.toString());
                case date ->
                        value instanceof LocalDate localDate
                                ? Date.valueOf(localDate)
                                : Date.valueOf(value.toString());
                case datetime ->
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

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(camelToSnake(key));
    }

    private String camelToSnake(String key) {
        StringBuilder result = new StringBuilder(key.length() + 4);
        for (int index = 0; index < key.length(); index++) {
            char ch = key.charAt(index);
            if (Character.isUpperCase(ch)) {
                result.append('_').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    public record ArchiveRecordQueryRequest(
            Long categoryId,
            ArchiveLevel archiveLevel,
            String fondsCode,
            String keyword,
            Map<String, Object> exactFilters,
            List<ArchiveRecordFieldFilter> filters) {}

    public record ArchiveRecordFieldFilter(
            String fieldCode, Object value, Object startValue, Object endValue) {}

    public record ArchiveRecordRequest(
            Long categoryId,
            ArchiveLevel archiveLevel,
            Long parentId,
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String electronicStatus,
            ArchivePhysicalObjectRequest physicalObject,
            Map<String, Object> dynamicFields) {}

    public record ArchiveRecordUpdateRequest(
            Long parentId,
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String electronicStatus,
            ArchivePhysicalObjectRequest physicalObject,
            Map<String, Object> dynamicFields) {}

    public record ArchivePhysicalObjectRequest(
            String physicalStatus,
            String boxNo,
            String locationNo,
            String barcode,
            String remark) {}

    public record DeleteRecordRequest(String reason) {}

    public record LockRecordRequest(String reason) {}

    public record ArchiveRecordDto(
            @JsonSerialize(using = LongStringSerializer.class) Long id,
            ArchiveLevel archiveLevel,
            @JsonSerialize(using = LongStringSerializer.class) Long parentId,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            String archiveNo,
            String electronicStatus,
            int archiveYear,
            boolean lockedFlag,
            String lockReason,
            @JsonSerialize(using = LongStringSerializer.class) Long lockedBy,
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
            Map<String, Object> dynamicFields,
            ArchivePhysicalObjectDto physicalObject) {}

    public record ArchivePhysicalObjectDto(
            @JsonSerialize(using = LongStringSerializer.class) Long id,
            @JsonSerialize(using = LongStringSerializer.class) Long archiveRecordId,
            String physicalStatus,
            String boxNo,
            String locationNo,
            String barcode,
            String remark) {}

    public record SearchProjectionRebuildResult(Long categoryId, int rebuiltCount) {}
}
