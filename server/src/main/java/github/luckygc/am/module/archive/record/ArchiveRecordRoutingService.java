package github.luckygc.am.module.archive.record;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintFieldDto;

@Service
public class ArchiveRecordRoutingService {

    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_UPDATE = "UPDATE";
    private static final String AUDIT_OPERATION_DELETE = "DELETE";
    private static final String AUDIT_OPERATION_LOCK = "LOCK";
    private static final String AUDIT_OPERATION_UNLOCK = "UNLOCK";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;
    private static final String CURSOR_HMAC_KEY = "archive-record-cursor-v1";

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveRecordSearchProjectionService searchProjectionService;

    public ArchiveRecordRoutingService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMapper archiveMapper,
            ArchiveRecordSearchProjectionService searchProjectionService) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMapper = archiveMapper;
        this.searchProjectionService = searchProjectionService;
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
                        categoryId, archiveLevel, fondsCode, null, null, null, null, null, null),
                userId);
    }

    public ArchiveRecordListDto searchRecords(ArchiveRecordQueryRequest request) {
        return searchRecords(request, null);
    }

    public ArchiveRecordListDto searchRecords(ArchiveRecordQueryRequest request, Long userId) {
        return queryRecords(request, userId, false, false);
    }

    public ArchiveRecordListDto discoverRecords(ArchiveRecordQueryRequest request, Long userId) {
        return queryRecords(request, userId, true, true);
    }

    private ArchiveRecordListDto queryRecords(
            ArchiveRecordQueryRequest request,
            Long userId,
            boolean allowKeyword,
            boolean requireAuthenticatedUser) {
        String keyword = StringUtils.trimToNull(request == null ? null : request.keyword());
        if (StringUtils.isNotBlank(keyword) && !allowKeyword) {
            throw badRequest(
                    "档案管理列表不支持全文关键词检索", "keyword", "档案管理列表只支持数据库字段筛选；全文检索用于查档、借阅等普通用户业务入口");
        }
        if (request == null || request.categoryId() == null) {
            if (StringUtils.isNotBlank(keyword)) {
                throw badRequest("全文检索必须选择档案分类", "categoryId", "全文检索必须选择档案分类");
            }
            int limit = pageLimit(request == null ? null : request.limit());
            List<Map<String, Object>> rows =
                    archiveMapper.listRecordOverview().stream().limit(limit).toList();
            return new ArchiveRecordListDto(null, List.of(), true, null, null, null, null, rows);
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
            return new ArchiveRecordListDto(
                    category, fields, false, null, null, null, null, List.of());
        }
        int limit = pageLimit(request.limit());
        List<ArchiveSqlOrder> orderBy = orderBy(request.orderBy());
        Cursor cursor = decodeCursor(request.cursor(), queryFingerprint(request, orderBy));
        List<ArchiveSqlCondition> conditions =
                buildSearchConditions(
                        request.categoryId(),
                        archiveLevel,
                        fields,
                        request.exactFilters(),
                        request.filters());
        List<ArchiveSqlOrder> queryOrderBy =
                cursor != null && "prev".equals(cursor.direction()) ? invert(orderBy) : orderBy;
        List<Map<String, Object>> rows =
                archiveMapper.listDynamicRecords(
                        tableName,
                        selectColumns(visibleFields),
                        archiveLevel.value(),
                        StringUtils.trimToNull(request.fondsCode()),
                        conditions,
                        keyword,
                        userId,
                        requireAuthenticatedUser,
                        orderBySql(queryOrderBy),
                        cursorPredicateSql(queryOrderBy, cursor),
                        cursorValues(cursor),
                        limit + 1);
        List<Map<String, Object>> normalizedRows = normalizeDynamicFieldValues(rows, visibleFields);
        boolean hasMore = normalizedRows.size() > limit;
        List<Map<String, Object>> pageItems =
                hasMore ? normalizedRows.subList(0, limit) : normalizedRows;
        if (cursor != null && "prev".equals(cursor.direction())) {
            pageItems = pageItems.reversed();
        }
        String fingerprint = queryFingerprint(request, orderBy);
        String prev =
                cursor == null || pageItems.isEmpty()
                        ? null
                        : encodeCursor("prev", fingerprint, orderBy, pageItems.get(0));
        String next =
                pageItems.isEmpty()
                                || (cursor != null && "prev".equals(cursor.direction()) && !hasMore)
                        ? null
                        : encodeCursor(
                                "next", fingerprint, orderBy, pageItems.get(pageItems.size() - 1));
        if ((cursor == null || "next".equals(cursor.direction())) && !hasMore) {
            next = null;
        }
        return new ArchiveRecordListDto(
                category,
                fields,
                true,
                encodeCursor(
                        "self",
                        fingerprint,
                        orderBy,
                        pageItems.isEmpty() ? null : pageItems.get(0)),
                prev,
                next,
                null,
                pageItems);
    }

    private int pageLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        if (limit < 1 || limit > MAX_PAGE_LIMIT) {
            throw badRequest("分页大小必须在 1 到 " + MAX_PAGE_LIMIT + " 之间", "limit", "分页大小超出范围");
        }
        return limit;
    }

    private List<ArchiveSqlOrder> orderBy(List<ArchiveRecordOrderBy> requestOrderBy) {
        List<ArchiveSqlOrder> orders = new ArrayList<>();
        if (requestOrderBy != null) {
            for (ArchiveRecordOrderBy order : requestOrderBy) {
                orders.add(toSqlOrder(order));
            }
        }
        appendFallbackOrder(orders, new ArchiveSqlOrder("r.created_at", Direction.desc));
        appendFallbackOrder(orders, new ArchiveSqlOrder("r.id", Direction.desc));
        return orders;
    }

    private void appendFallbackOrder(List<ArchiveSqlOrder> orders, ArchiveSqlOrder fallback) {
        boolean alreadyOrdered =
                orders.stream()
                        .anyMatch(
                                order -> Objects.equals(order.expression(), fallback.expression()));
        if (!alreadyOrdered) {
            orders.add(fallback);
        }
    }

    private ArchiveSqlOrder toSqlOrder(ArchiveRecordOrderBy order) {
        if (order == null || StringUtils.isBlank(order.field())) {
            throw badRequest("排序字段不能为空", "orderBy.field", "排序字段不能为空");
        }
        Direction direction =
                "ASC".equalsIgnoreCase(order.direction()) ? Direction.asc : Direction.desc;
        return new ArchiveSqlOrder(sortExpression(order.field()), direction);
    }

    private String sortExpression(String field) {
        return switch (field) {
            case "createdAt" -> "r.created_at";
            case "archiveNo" -> "r.archive_no";
            case "archiveYear" -> "r.archive_year";
            case "fondsCode" -> "r.fonds_code";
            case "categoryCode" -> "r.category_code";
            case "electronicStatus" -> "r.electronic_status";
            case "id" -> "r.id";
            default -> throw badRequest("不支持的排序字段", "orderBy.field", "不支持的排序字段：" + field);
        };
    }

    private String orderBySql(List<ArchiveSqlOrder> orders) {
        return orders.stream()
                .map(ArchiveSqlOrder::sql)
                .reduce((left, right) -> left + ", " + right)
                .orElse("r.created_at desc, r.id desc");
    }

    private List<ArchiveSqlOrder> invert(List<ArchiveSqlOrder> orders) {
        return orders.stream()
                .map(
                        order ->
                                new ArchiveSqlOrder(
                                        order.expression(),
                                        order.direction() == Direction.asc
                                                ? Direction.desc
                                                : Direction.asc))
                .toList();
    }

    private String cursorPredicateSql(List<ArchiveSqlOrder> orders, Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            List<String> equalsParts = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                equalsParts.add(orders.get(j).expression() + " = #{cursorValues[" + j + "]}");
            }
            String operator = orders.get(i).direction() == Direction.asc ? ">" : "<";
            equalsParts.add(
                    orders.get(i).expression() + " " + operator + " #{cursorValues[" + i + "]}");
            parts.add("(" + String.join(" and ", equalsParts) + ")");
        }
        return String.join(" or ", parts);
    }

    private List<Object> cursorValues(Cursor cursor) {
        return cursor == null ? List.of() : cursor.values();
    }

    private Cursor decodeCursor(String token, String fingerprint) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw badRequest("分页 cursor 无效", "cursor", "cursor 格式无效");
        }
        String payload =
                new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String expected = HmacUtils.hmacSha256Hex(CURSOR_HMAC_KEY, payload);
        if (!Objects.equals(expected, parts[1])) {
            throw badRequest("分页 cursor 无效", "cursor", "cursor 签名无效");
        }
        String[] fields = payload.split("\\|", -1);
        if (fields.length < 3 || !Objects.equals(fingerprint, fields[1])) {
            throw badRequest("分页条件已变化，请重新搜索", "cursor", "cursor 与当前查询条件不匹配");
        }
        List<Object> values = new ArrayList<>();
        for (int i = 2; i < fields.length; i++) {
            values.add(decodeCursorValue(fields[i]));
        }
        return new Cursor(fields[0], values);
    }

    private Object decodeCursorValue(String value) {
        if (value.startsWith("L:")) {
            return Long.valueOf(value.substring(2));
        }
        if (value.startsWith("I:")) {
            return Integer.valueOf(value.substring(2));
        }
        if (value.startsWith("T:")) {
            return LocalDateTime.parse(value.substring(2));
        }
        return value.substring(2);
    }

    private String encodeCursor(
            String direction,
            String fingerprint,
            List<ArchiveSqlOrder> orders,
            Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (ArchiveSqlOrder order : orders) {
            values.add(encodeCursorValue(cursorRowValue(row, order.expression())));
        }
        String payload = direction + "|" + fingerprint + "|" + String.join("|", values);
        return Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + HmacUtils.hmacSha256Hex(CURSOR_HMAC_KEY, payload);
    }

    private Object cursorRowValue(Map<String, Object> row, String expression) {
        return switch (expression) {
            case "r.created_at" -> value(row, "createdAt");
            case "r.id" -> longCursorValue(row, "id");
            case "r.archive_no" -> value(row, "archiveNo");
            case "r.archive_year" -> integerCursorValue(row, "archiveYear");
            case "r.fonds_code" -> value(row, "fondsCode");
            case "r.category_code" -> value(row, "categoryCode");
            case "r.electronic_status" -> value(row, "electronicStatus");
            default -> throw new IllegalStateException("不支持的 cursor 排序字段：" + expression);
        };
    }

    private Long longCursorValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Integer integerCursorValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private String encodeCursorValue(Object value) {
        if (value instanceof Long longValue) {
            return "L:" + longValue;
        }
        if (value instanceof Integer intValue) {
            return "I:" + intValue;
        }
        if (value instanceof Number number) {
            return "L:" + number.longValue();
        }
        if (value instanceof LocalDateTime dateTime) {
            return "T:" + dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return "T:" + timestamp.toLocalDateTime();
        }
        return "S:" + value;
    }

    private String queryFingerprint(
            ArchiveRecordQueryRequest request, List<ArchiveSqlOrder> orderBy) {
        Map<String, Object> exactFilters =
                request.exactFilters() == null ? Map.of() : new TreeMap<>(request.exactFilters());
        return Integer.toHexString(
                Objects.hash(
                        request.categoryId(),
                        normalizeArchiveLevel(request.archiveLevel()),
                        StringUtils.trimToNull(request.fondsCode()),
                        StringUtils.trimToNull(request.keyword()),
                        exactFilters,
                        request.filters(),
                        request.limit(),
                        orderBy.stream().map(ArchiveSqlOrder::sql).toList()));
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
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEnabledFields(
                        request.categoryId(), archiveLevel, ArchiveFieldScope.physical);
        Map<String, Object> dynamicFields =
                request.dynamicFields() == null ? Map.of() : request.dynamicFields();
        Map<String, Object> requestPhysicalFields = request.physicalFields();
        int archiveYear =
                request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, Object> convertedDynamicFields = convertDynamicFields(fields, dynamicFields);
        Map<String, Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : convertDynamicFields(physicalFields, requestPhysicalFields);

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
            insertDynamicRecord(tableName, recordId, fields, convertedDynamicFields);
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案记录违反唯一约束");
        }
        if (requestPhysicalFields != null) {
            upsertPhysicalFieldsIfPresent(
                    category, archiveLevel, recordId, physicalFields, convertedPhysicalFields);
        }
        searchProjectionService.upsert(recordId, fields, convertedDynamicFields);
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
                searchProjectionService.enqueueUpsert(number(row, "id").longValue());
                rebuilt++;
            }
        }
        searchProjectionService.drainOutbox();
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
                        ArchiveFieldScope.metadata,
                        surface == null ? ArchiveLayoutSurface.detail : surface,
                        userId);
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEffectiveFields(
                        category.id(),
                        record.archiveLevel(),
                        ArchiveFieldScope.physical,
                        surface == null ? ArchiveLayoutSurface.detail : surface,
                        userId);
        Map<String, Object> dynamicRecord = loadDynamicRecord(category, record.id());
        Map<String, Object> physicalRecord =
                loadDynamicRecord(category, record.id(), ArchiveFieldScope.physical);
        return new ArchiveRecordDetailDto(
                record,
                category,
                fields,
                dynamicFieldsByCode(dynamicRecord, fields),
                physicalFields,
                dynamicFieldsByCode(physicalRecord, physicalFields));
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
        Map<String, Object> requestPhysicalFields = request.physicalFields();
        Map<String, Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : convertDynamicFields(before.physicalFields(), requestPhysicalFields);
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
                    tableName, id, dynamicAssignments(before.fields(), convertedDynamicFields));
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案记录违反唯一约束");
        }
        if (requestPhysicalFields != null) {
            upsertPhysicalFieldsIfPresent(
                    category,
                    before.record().archiveLevel(),
                    id,
                    before.physicalFields(),
                    convertedPhysicalFields);
        }
        searchProjectionService.refreshFromDynamicRecord(
                id, category, before.record().archiveLevel());
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
        String physicalTableName =
                dynamicTableName(category, record.archiveLevel(), ArchiveFieldScope.physical);
        if (isDynamicTableBuilt(category, record.archiveLevel(), ArchiveFieldScope.physical)) {
            archiveMapper.markDynamicRecordDeleted(physicalTableName, id);
        }
        int updated = archiveMapper.markArchiveRecordDeleted(id, userId);
        if (updated == 0) {
            throw badRequest("档案记录已锁定，不能删除");
        }
        searchProjectionService.delete(id);
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

    private void upsertPhysicalFieldsIfPresent(
            ArchiveCategoryDto category,
            ArchiveLevel archiveLevel,
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, Object> convertedFields) {
        if (fields.isEmpty() || convertedFields.isEmpty()) {
            return;
        }
        if (!isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.physical)) {
            throw badRequest("档案分类实物信息尚未建表");
        }
        String tableName = dynamicTableName(category, archiveLevel, ArchiveFieldScope.physical);
        Map<String, Object> current = archiveMapper.loadDynamicRecord(tableName, recordId);
        if (current == null) {
            insertDynamicRecord(tableName, recordId, fields, convertedFields);
        } else {
            archiveMapper.updateDynamicRecord(
                    tableName, recordId, dynamicAssignments(fields, convertedFields));
        }
    }

    private Map<String, Object> loadDynamicRecord(ArchiveCategoryDto category, Long id) {
        ArchiveRecordDto record = getRecord(id);
        return loadDynamicRecord(category, record.archiveLevel(), id, ArchiveFieldScope.metadata);
    }

    private Map<String, Object> loadDynamicRecord(
            ArchiveCategoryDto category, Long id, ArchiveFieldScope fieldScope) {
        ArchiveRecordDto record = getRecord(id);
        return loadDynamicRecord(category, record.archiveLevel(), id, fieldScope);
    }

    private Map<String, Object> loadDynamicRecord(
            ArchiveCategoryDto category,
            ArchiveLevel archiveLevel,
            Long id,
            ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        if (!isDynamicTableBuilt(category, archiveLevel, fieldScope)) {
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
        return dynamicTableName(category, archiveLevel, ArchiveFieldScope.metadata);
    }

    private String dynamicTableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.tableName(category, archiveLevel, fieldScope);
    }

    private ArchiveLevel normalizeArchiveLevel(ArchiveLevel archiveLevel) {
        return ArchiveDynamicTableNames.normalizeArchiveLevel(archiveLevel);
    }

    private void ensureArchiveLevelAllowed(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        if (!ArchiveDynamicTableNames.isVolumeLevelAllowed(category, archiveLevel)) {
            throw badRequest("该分类未启用案卷管理");
        }
    }

    private boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.metadata);
    }

    private boolean isDynamicTableBuilt(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
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
                            Map<String, Object> normalized = new LinkedHashMap<>(row);
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
            List<ArchiveFieldDto> fields,
            Map<String, Object> convertedDynamicFields) {
        StringBuilder columns = new StringBuilder("id");
        List<Object> values = new ArrayList<>();
        values.add(recordId);
        for (ArchiveFieldDto field : fields) {
            columns.append(", ").append(field.columnName());
            values.add(convertedDynamicFields.get(field.fieldCode()));
        }
        archiveMapper.insertDynamicRecord(tableName, columns.toString(), values);
    }

    private List<ArchiveSqlAssignment> dynamicAssignments(
            List<ArchiveFieldDto> fields, Map<String, Object> convertedDynamicFields) {
        List<ArchiveSqlAssignment> assignments = new ArrayList<>();
        for (ArchiveFieldDto field : fields) {
            assignments.add(
                    new ArchiveSqlAssignment(
                            field.columnName(), convertedDynamicFields.get(field.fieldCode())));
        }
        return assignments;
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
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveRecordQueryRequest(
            Long categoryId,
            ArchiveLevel archiveLevel,
            String fondsCode,
            String keyword,
            Map<String, Object> exactFilters,
            List<ArchiveRecordFieldFilter> filters,
            Integer limit,
            String cursor,
            List<ArchiveRecordOrderBy> orderBy) {}

    public record ArchiveRecordOrderBy(String field, String direction) {}

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
            Map<String, Object> physicalFields,
            Map<String, Object> dynamicFields) {}

    public record ArchiveRecordUpdateRequest(
            Long parentId,
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String electronicStatus,
            Map<String, Object> physicalFields,
            Map<String, Object> dynamicFields) {}

    public record DeleteRecordRequest(String reason) {}

    public record LockRecordRequest(String reason) {}

    public record ArchiveRecordDto(
            Long id,
            ArchiveLevel archiveLevel,
            Long parentId,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            String archiveNo,
            String electronicStatus,
            int archiveYear,
            boolean lockedFlag,
            String lockReason,
            Long lockedBy,
            LocalDateTime lockedAt) {}

    public record ArchiveRecordListDto(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            boolean tableBuilt,
            String self,
            String prev,
            String next,
            String first,
            List<Map<String, Object>> items) {}

    private record Cursor(String direction, List<Object> values) {}

    public record ArchiveRecordDetailDto(
            ArchiveRecordDto record,
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            Map<String, Object> dynamicFields,
            List<ArchiveFieldDto> physicalFields,
            Map<String, Object> physicalFieldValues) {}

    public record SearchProjectionRebuildResult(Long categoryId, int rebuiltCount) {}
}
