package github.luckygc.am.module.archive.item;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
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
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintFieldDto;

@Service
public class ArchiveItemRoutingService {

    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_UPDATE = "UPDATE";
    private static final String AUDIT_OPERATION_DELETE = "DELETE";
    private static final String AUDIT_OPERATION_LOCK = "LOCK";
    private static final String AUDIT_OPERATION_UNLOCK = "UNLOCK";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;
    private static final String CURSOR_HMAC_KEY = "archive-item-cursor-v1";
    private static final LocalDateTime CURSOR_TIME_EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveItemSearchProjectionService searchProjectionService;

    public ArchiveItemRoutingService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMapper archiveMapper,
            ArchiveItemSearchProjectionService searchProjectionService) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMapper = archiveMapper;
        this.searchProjectionService = searchProjectionService;
    }

    public ArchiveItemListDto listItems(@Nullable Long categoryId, @Nullable String fondsCode) {
        return listItems(categoryId, fondsCode, null);
    }

    public ArchiveItemListDto listItems(
            @Nullable Long categoryId, @Nullable String fondsCode, @Nullable Long userId) {
        return searchItems(
                new ArchiveItemQueryRequest(
                        categoryId, fondsCode, null, null, null, null, null, null),
                userId);
    }

    public ArchiveItemListDto searchItems(@Nullable ArchiveItemQueryRequest request) {
        return searchItems(request, null);
    }

    public ArchiveItemListDto searchItems(
            @Nullable ArchiveItemQueryRequest request, @Nullable Long userId) {
        return queryItems(request, userId, false, false, false);
    }

    public ArchiveItemListDto discoverItems(
            @Nullable ArchiveItemQueryRequest request, @Nullable Long userId) {
        return queryItems(request, userId, true, true, false);
    }

    public ArchiveItemListDto searchDeletedItems(
            @Nullable ArchiveItemQueryRequest request, @Nullable Long userId) {
        return queryItems(request, userId, false, false, true);
    }

    private ArchiveItemListDto queryItems(
            @Nullable ArchiveItemQueryRequest request,
            @Nullable Long userId,
            boolean allowKeyword,
            boolean requireAuthenticatedUser,
            boolean deleted) {
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
            List<Map<String, @Nullable Object>> rows =
                    archiveMapper.listItemOverview().stream().limit(limit).toList();
            return new ArchiveItemListDto(null, List.of(), true, null, null, null, null, rows);
        }

        ArchiveCategoryDto category = archiveMetadataService.getCategory(request.categoryId());
        ArchiveLevel archiveLevel = ArchiveLevel.ITEM;
        ensureArchiveLevelAllowed(category, archiveLevel);
        String tableName = dynamicTableName(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEffectiveFields(
                        request.categoryId(), archiveLevel, ArchiveLayoutSurface.TABLE, userId);
        List<ArchiveFieldDto> visibleFields =
                fields.stream()
                        .filter(ArchiveFieldDto::listVisible)
                        .sorted(
                                java.util.Comparator.comparingInt(ArchiveFieldDto::listSortOrder)
                                        .thenComparing(ArchiveFieldDto::id))
                        .toList();
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            return new ArchiveItemListDto(
                    category, fields, false, null, null, null, null, List.of());
        }
        int limit = pageLimit(request.limit());
        List<ArchiveSqlOrder> orderBy = deleted ? deletedOrderBy() : orderBy(request.orderBy());
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
        List<Map<String, @Nullable Object>> rows =
                archiveMapper.listDynamicItems(
                        tableName,
                        selectColumns(visibleFields),
                        archiveLevel.value(),
                        deleted,
                        StringUtils.trimToNull(request.fondsCode()),
                        conditions,
                        keyword,
                        userId,
                        requireAuthenticatedUser,
                        orderBySql(queryOrderBy),
                        cursorPredicateSql(queryOrderBy, cursor),
                        cursorValues(cursor),
                        limit + 1);
        List<Map<String, @Nullable Object>> normalizedRows =
                normalizeDynamicFieldValues(rows, visibleFields);
        boolean hasMore = normalizedRows.size() > limit;
        List<Map<String, @Nullable Object>> pageItems =
                hasMore ? normalizedRows.subList(0, limit) : normalizedRows;
        if (cursor != null && "prev".equals(cursor.direction())) {
            pageItems = pageItems.reversed();
        }
        String fingerprint = queryFingerprint(request, orderBy);
        String prev =
                cursor == null || pageItems.isEmpty()
                        ? null
                        : encodeCursor("prev", fingerprint, orderBy, pageItems.getFirst());
        String next =
                pageItems.isEmpty()
                                || (cursor != null && "prev".equals(cursor.direction()) && !hasMore)
                        ? null
                        : encodeCursor("next", fingerprint, orderBy, pageItems.getLast());
        if ((cursor == null || "next".equals(cursor.direction())) && !hasMore) {
            next = null;
        }
        return new ArchiveItemListDto(
                category,
                fields,
                true,
                encodeCursor(
                        "self",
                        fingerprint,
                        orderBy,
                        pageItems.isEmpty() ? null : pageItems.getFirst()),
                prev,
                next,
                null,
                pageItems);
    }

    private int pageLimit(@Nullable Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        if (limit < 1 || limit > MAX_PAGE_LIMIT) {
            throw badRequest("分页大小必须在 1 到 " + MAX_PAGE_LIMIT + " 之间", "limit", "分页大小超出范围");
        }
        return limit;
    }

    private List<ArchiveSqlOrder> orderBy(
            @Nullable List<@Nullable ArchiveItemOrderBy> requestOrderBy) {
        List<ArchiveSqlOrder> orders = new ArrayList<>();
        if (requestOrderBy != null) {
            for (ArchiveItemOrderBy order : requestOrderBy) {
                orders.add(toSqlOrder(order));
            }
        }
        appendFallbackOrder(orders, new ArchiveSqlOrder("r.created_at", Direction.DESC));
        appendFallbackOrder(orders, new ArchiveSqlOrder("r.id", Direction.DESC));
        return orders;
    }

    private List<ArchiveSqlOrder> deletedOrderBy() {
        return List.of(
                new ArchiveSqlOrder("r.deleted_at", Direction.DESC),
                new ArchiveSqlOrder("r.id", Direction.DESC));
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

    private ArchiveSqlOrder toSqlOrder(@Nullable ArchiveItemOrderBy order) {
        if (order == null || StringUtils.isBlank(order.field())) {
            throw badRequest("排序字段不能为空", "orderBy.field", "排序字段不能为空");
        }
        Direction direction =
                "ASC".equalsIgnoreCase(order.direction()) ? Direction.ASC : Direction.DESC;
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
                                        order.direction() == Direction.ASC
                                                ? Direction.DESC
                                                : Direction.ASC))
                .toList();
    }

    private @Nullable String cursorPredicateSql(
            List<ArchiveSqlOrder> orders, @Nullable Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            List<String> equalsParts = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                equalsParts.add(orders.get(j).expression() + " = #{cursorValues[" + j + "]}");
            }
            String operator = orders.get(i).direction() == Direction.ASC ? ">" : "<";
            equalsParts.add(
                    orders.get(i).expression() + " " + operator + " #{cursorValues[" + i + "]}");
            parts.add("(" + String.join(" and ", equalsParts) + ")");
        }
        return String.join(" or ", parts);
    }

    private List<Object> cursorValues(@Nullable Cursor cursor) {
        return cursor == null ? List.of() : cursor.values();
    }

    private @Nullable Cursor decodeCursor(@Nullable String token, String fingerprint) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw badRequest("分页 cursor 无效", "cursor", "cursor 格式无效");
        }
        String payload =
                new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String expected = cursorHmacHex(payload);
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
        if (value.startsWith("U:")) {
            return CURSOR_TIME_EPOCH.plus(Long.parseLong(value.substring(2)), ChronoUnit.MICROS);
        }
        return value.substring(2);
    }

    private @Nullable String encodeCursor(
            String direction,
            String fingerprint,
            List<ArchiveSqlOrder> orders,
            @Nullable Map<String, @Nullable Object> row) {
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
                + cursorHmacHex(payload);
    }

    private String cursorHmacHex(String payload) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, CURSOR_HMAC_KEY).hmacHex(payload);
    }

    private @Nullable Object cursorRowValue(Map<String, @Nullable Object> row, String expression) {
        return switch (expression) {
            case "r.created_at" -> value(row, "createdAt");
            case "r.deleted_at" -> value(row, "deletedAt");
            case "r.id" -> longCursorValue(row, "id");
            case "r.archive_no" -> value(row, "archiveNo");
            case "r.archive_year" -> integerCursorValue(row, "archiveYear");
            case "r.fonds_code" -> value(row, "fondsCode");
            case "r.category_code" -> value(row, "categoryCode");
            case "r.electronic_status" -> value(row, "electronicStatus");
            default -> throw new IllegalStateException("不支持的 cursor 排序字段：" + expression);
        };
    }

    private Long longCursorValue(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Integer integerCursorValue(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private String encodeCursorValue(@Nullable Object value) {
        return switch (value) {
            case Long longValue -> "L:" + longValue;
            case Integer intValue -> "I:" + intValue;
            case Number number -> "L:" + number.longValue();
            case LocalDateTime dateTime -> "U:" + cursorTimeMicros(dateTime);
            case Timestamp timestamp -> "U:" + cursorTimeMicros(timestamp.toLocalDateTime());
            default -> "S:" + value;
        };
    }

    private long cursorTimeMicros(LocalDateTime dateTime) {
        return ChronoUnit.MICROS.between(
                CURSOR_TIME_EPOCH, dateTime.truncatedTo(ChronoUnit.MICROS));
    }

    private String queryFingerprint(
            ArchiveItemQueryRequest request, List<ArchiveSqlOrder> orderBy) {
        Map<String, @Nullable Object> exactFilters =
                request.exactFilters() == null ? Map.of() : new TreeMap<>(request.exactFilters());
        return Integer.toHexString(
                Objects.hash(
                        request.categoryId(),
                        ArchiveLevel.ITEM,
                        StringUtils.trimToNull(request.fondsCode()),
                        StringUtils.trimToNull(request.keyword()),
                        exactFilters,
                        request.filters(),
                        request.limit(),
                        orderBy.stream().map(ArchiveSqlOrder::sql).toList()));
    }

    @Transactional
    public ArchiveItemDto createItem(@Nullable ArchiveItemRequest request, @Nullable Long userId) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (request.categoryId() == null) {
            throw badRequest("档案分类不能为空", "categoryId", "档案分类不能为空");
        }
        ArchiveCategoryDto category = archiveMetadataService.getCategory(request.categoryId());
        ArchiveLevel archiveLevel = ArchiveLevel.ITEM;
        ensureArchiveLevelAllowed(category, archiveLevel);
        String tableName = dynamicTableName(category, archiveLevel);
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        Long volumeId =
                validateParentForWrite(
                        archiveLevel,
                        request.volumeId(),
                        category.categoryCode(),
                        fonds.fondsCode());
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(request.categoryId(), archiveLevel);
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEnabledFields(
                        request.categoryId(), archiveLevel, ArchiveFieldScope.PHYSICAL);
        Map<String, @Nullable Object> dynamicFields =
                request.dynamicFields() == null ? Map.of() : request.dynamicFields();
        Map<String, @Nullable Object> requestPhysicalFields = request.physicalFields();
        int archiveYear =
                request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, @Nullable Object> convertedDynamicFields =
                convertDynamicFields(fields, dynamicFields);
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : convertDynamicFields(physicalFields, requestPhysicalFields);

        Long recordId =
                archiveMapper.insertArchiveItem(
                        archiveLevel.value(),
                        volumeId,
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
            throw badRequest("档案条目违反唯一约束");
        }
        if (requestPhysicalFields != null) {
            upsertPhysicalFieldsIfPresent(
                    category, archiveLevel, recordId, physicalFields, convertedPhysicalFields);
        }
        searchProjectionService.upsert(recordId, category, fields, convertedDynamicFields);
        ArchiveItemDto record = getItem(recordId);
        insertItemAudit(AUDIT_OPERATION_CREATE, record, null, userId);
        return record;
    }

    @Transactional
    public SearchProjectionRebuildResult rebuildSearchProjection(Long categoryId) {
        ArchiveCategoryDto category = archiveMetadataService.getCategory(categoryId);
        int rebuilt = 0;
        ArchiveLevel archiveLevel = ArchiveLevel.ITEM;
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            return new SearchProjectionRebuildResult(categoryId, 0);
        }
        String tableName = dynamicTableName(category, archiveLevel);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(categoryId, archiveLevel);
        if (fields.isEmpty()) {
            return new SearchProjectionRebuildResult(categoryId, 0);
        }
        List<Map<String, @Nullable Object>> rows =
                archiveMapper.listItemsForSearchRebuild(
                        tableName, selectColumns(List.of()), archiveLevel.value());
        for (Map<String, @Nullable Object> row : rows) {
            searchProjectionService.enqueueUpsert(number(row, "id").longValue());
            rebuilt++;
        }
        searchProjectionService.drainOutbox();
        return new SearchProjectionRebuildResult(categoryId, rebuilt);
    }

    public ArchiveItemDetailDto getItemDetail(Long id) {
        return getItemDetail(id, null, ArchiveLayoutSurface.DETAIL);
    }

    public ArchiveItemDetailDto getItemDetail(
            Long id, @Nullable Long userId, @Nullable ArchiveLayoutSurface surface) {
        ArchiveItemDto record = getItem(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEffectiveFields(
                        category.id(),
                        ArchiveLevel.ITEM,
                        ArchiveFieldScope.METADATA,
                        surface == null ? ArchiveLayoutSurface.DETAIL : surface,
                        userId);
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEffectiveFields(
                        category.id(),
                        ArchiveLevel.ITEM,
                        ArchiveFieldScope.PHYSICAL,
                        surface == null ? ArchiveLayoutSurface.DETAIL : surface,
                        userId);
        Map<String, @Nullable Object> dynamicRecord = loadDynamicRecord(category, record.id());
        Map<String, @Nullable Object> physicalRecord =
                loadDynamicRecord(category, record.id(), ArchiveFieldScope.PHYSICAL);
        return new ArchiveItemDetailDto(
                record,
                category,
                fields,
                dynamicFieldsByCode(dynamicRecord, fields),
                physicalFields,
                dynamicFieldsByCode(physicalRecord, physicalFields));
    }

    @Transactional
    public ArchiveItemDetailDto updateItem(
            Long id, @Nullable ArchiveItemUpdateRequest request, @Nullable Long userId) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        ArchiveItemDetailDto before = getItemDetail(id);
        ensureItemEditable(before.item());
        ArchiveCategoryDto category = before.category();
        String tableName = dynamicTableName(category, ArchiveLevel.ITEM);
        if (!isDynamicTableBuilt(category, ArchiveLevel.ITEM)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        Long volumeId =
                validateParentForWrite(
                        ArchiveLevel.ITEM,
                        request.volumeId() == null ? before.item().volumeId() : request.volumeId(),
                        before.item().categoryCode(),
                        fonds.fondsCode());
        int archiveYear =
                request.archiveYear() == null ? before.item().archiveYear() : request.archiveYear();
        validateArchiveYear(archiveYear);
        Map<String, @Nullable Object> requestDynamicFields =
                request.dynamicFields() == null ? before.dynamicFields() : request.dynamicFields();
        Map<String, @Nullable Object> convertedDynamicFields =
                convertDynamicFields(before.fields(), requestDynamicFields);
        Map<String, @Nullable Object> requestPhysicalFields = request.physicalFields();
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : convertDynamicFields(before.physicalFields(), requestPhysicalFields);
        int updated =
                archiveMapper.updateArchiveItem(
                        id,
                        volumeId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        StringUtils.trimToNull(request.archiveNo()),
                        StringUtils.defaultIfBlank(
                                request.electronicStatus(), before.item().electronicStatus()),
                        archiveYear,
                        userId);
        if (updated == 0) {
            throw badRequest("档案条目已锁定，不能修改");
        }
        try {
            archiveMapper.updateDynamicRecord(
                    tableName, id, dynamicAssignments(before.fields(), convertedDynamicFields));
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案条目违反唯一约束");
        }
        if (requestPhysicalFields != null) {
            upsertPhysicalFieldsIfPresent(
                    category,
                    ArchiveLevel.ITEM,
                    id,
                    before.physicalFields(),
                    convertedPhysicalFields);
        }
        searchProjectionService.refreshFromDynamicRecord(id, category, ArchiveLevel.ITEM);
        ArchiveItemDetailDto after = getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        insertItemAudit(AUDIT_OPERATION_UPDATE, after.item(), null, userId);
        return after;
    }

    @Transactional
    public void deleteItem(Long id, @Nullable Long userId, @Nullable DeleteItemRequest request) {
        ArchiveItemDto record = getItem(id);
        ensureItemEditable(record);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        String tableName = dynamicTableName(category, ArchiveLevel.ITEM);
        insertItemAudit(
                AUDIT_OPERATION_DELETE,
                record,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        if (isDynamicTableBuilt(category, ArchiveLevel.ITEM)) {
            archiveMapper.markDynamicRecordDeleted(tableName, id, userId);
        }
        String physicalTableName =
                dynamicTableName(category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL);
        if (isDynamicTableBuilt(category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL)) {
            archiveMapper.markDynamicRecordDeleted(physicalTableName, id, userId);
        }
        int updated = archiveMapper.markArchiveItemDeleted(id, userId);
        if (updated == 0) {
            throw badRequest("档案条目已锁定，不能删除");
        }
        searchProjectionService.delete(id);
    }

    @Transactional
    public ArchiveItemDto lockItem(
            Long id, @Nullable Long userId, @Nullable LockItemRequest request) {
        if (id == null || id <= 0) {
            throw badRequest("档案条目 ID 不合法");
        }
        int updated =
                archiveMapper.lockArchiveItem(
                        id,
                        request == null ? null : StringUtils.trimToNull(request.reason()),
                        userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
        ArchiveItemDto after = getItem(id);
        insertItemAudit(
                AUDIT_OPERATION_LOCK,
                after,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        return after;
    }

    @Transactional
    public ArchiveItemDto unlockItem(Long id, @Nullable Long userId) {
        if (id == null || id <= 0) {
            throw badRequest("档案条目 ID 不合法");
        }
        int updated = archiveMapper.unlockArchiveItem(id, userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
        ArchiveItemDto after = getItem(id);
        insertItemAudit(AUDIT_OPERATION_UNLOCK, after, null, userId);
        return after;
    }

    public List<ArchiveItemRelationDto> listRelations(Long itemId) {
        return listRelations(itemId, 1);
    }

    public List<ArchiveItemRelationDto> listRelations(Long itemId, @Nullable Integer depth) {
        int normalizedDepth = depth == null ? 1 : depth;
        if (normalizedDepth < 1 || normalizedDepth > 2) {
            throw badRequest("关联档案展开层级必须在 1 到 2 之间", "depth", "关联档案展开层级必须在 1 到 2 之间");
        }
        getItem(itemId);
        return archiveMapper.listItemRelations(itemId).stream()
                .map(
                        row ->
                                new ArchiveItemRelationDto(
                                        number(row, "id").longValue(),
                                        number(row, "sourceItemId").longValue(),
                                        number(row, "targetItemId").longValue(),
                                        string(row, "relationType"),
                                        string(row, "remark"),
                                        number(row, "sortOrder").intValue(),
                                        new ArchiveItemRelationTargetDto(
                                                number(row, "targetItemId").longValue(),
                                                string(row, "targetFondsCode"),
                                                string(row, "targetFondsName"),
                                                string(row, "targetCategoryCode"),
                                                string(row, "targetCategoryName"),
                                                string(row, "targetArchiveNo"))))
                .toList();
    }

    @Transactional
    public ArchiveItemRelationDto createRelation(
            Long itemId, @Nullable ArchiveItemRelationRequest request, @Nullable Long userId) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (request.targetItemId() == null || request.targetItemId() <= 0) {
            throw badRequest("目标条目不能为空", "targetItemId", "目标条目不能为空");
        }
        if (itemId.equals(request.targetItemId())) {
            throw badRequest("不能关联自身", "targetItemId", "不能关联自身");
        }
        getItem(itemId);
        getItem(request.targetItemId());
        String relationType = StringUtils.defaultIfBlank(request.relationType(), "related");
        try {
            archiveMapper.insertItemRelation(
                    itemId,
                    request.targetItemId(),
                    relationType,
                    StringUtils.trimToNull(request.remark()),
                    request.sortOrder() == null ? 0 : request.sortOrder(),
                    userId);
        } catch (DuplicateKeyException exception) {
            throw badRequest("关联档案已存在", "targetItemId", "关联档案已存在");
        }
        return listRelations(itemId).stream()
                .filter(
                        relation ->
                                relation.targetItemId().equals(request.targetItemId())
                                        && relation.relationType().equals(relationType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("关联档案创建后不可见"));
    }

    @Transactional
    public void deleteRelation(Long itemId, @Nullable Long relationId, @Nullable Long userId) {
        if (relationId == null || relationId <= 0) {
            throw badRequest("关联 ID 不合法");
        }
        getItem(itemId);
        int updated = archiveMapper.deleteItemRelation(relationId, itemId, userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在");
        }
    }

    public ArchiveItemDto getItem(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("档案条目 ID 不合法");
        }
        Map<String, @Nullable Object> row = archiveMapper.getArchiveItem(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
        return new ArchiveItemDto(
                number(row, "id").longValue(),
                longOrNull(row, "volumeId"),
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
            Map<String, @Nullable Object> convertedFields) {
        if (fields.isEmpty() || convertedFields.isEmpty()) {
            return;
        }
        if (!isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.PHYSICAL)) {
            throw badRequest("档案分类实物信息尚未建表");
        }
        String tableName = dynamicTableName(category, archiveLevel, ArchiveFieldScope.PHYSICAL);
        Map<String, @Nullable Object> current =
                archiveMapper.loadDynamicRecord(tableName, recordId);
        if (current == null) {
            insertDynamicRecord(tableName, recordId, fields, convertedFields);
        } else {
            archiveMapper.updateDynamicRecord(
                    tableName, recordId, dynamicAssignments(fields, convertedFields));
        }
    }

    private Map<String, @Nullable Object> loadDynamicRecord(ArchiveCategoryDto category, Long id) {
        ArchiveItemDto record = getItem(id);
        return loadDynamicRecord(category, ArchiveLevel.ITEM, id, ArchiveFieldScope.METADATA);
    }

    private Map<String, @Nullable Object> loadDynamicRecord(
            ArchiveCategoryDto category, Long id, ArchiveFieldScope fieldScope) {
        ArchiveItemDto record = getItem(id);
        return loadDynamicRecord(category, ArchiveLevel.ITEM, id, fieldScope);
    }

    private Map<String, @Nullable Object> loadDynamicRecord(
            ArchiveCategoryDto category,
            ArchiveLevel archiveLevel,
            Long id,
            ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        if (!isDynamicTableBuilt(category, archiveLevel, fieldScope)) {
            return Map.of();
        }
        Map<String, @Nullable Object> dynamicRecord =
                archiveMapper.loadDynamicRecord(tableName, id);
        return dynamicRecord == null ? Map.of() : dynamicRecord;
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

    private Long validateParentForWrite(
            ArchiveLevel archiveLevel,
            @Nullable Long volumeId,
            String categoryCode,
            String fondsCode) {
        if (archiveLevel == ArchiveLevel.VOLUME) {
            if (volumeId != null) {
                throw badRequest("案卷不能设置父记录");
            }
            return null;
        }
        return volumeId;
    }

    public void ensureItemEditable(Long id) {
        ensureItemEditable(getItem(id));
    }

    private void ensureItemEditable(ArchiveItemDto record) {
        if (record.lockedFlag()) {
            throw badRequest("档案条目已锁定，不能修改");
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
            @Nullable Map<String, @Nullable Object> exactFilters,
            @Nullable List<@Nullable ArchiveItemFieldFilter> filters) {
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
            for (ArchiveItemFieldFilter filter : filters) {
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
            @Nullable ArchiveFieldDto field, String fieldCode, List<String> uniqueFieldCodes) {
        if (field == null
                || (!field.exactSearchable() && !uniqueFieldCodes.contains(field.fieldCode()))) {
            throw badRequest("字段不允许作为筛选条件：" + fieldCode);
        }
    }

    private List<ArchiveSqlCondition> toSearchConditions(
            ArchiveFieldDto field, ArchiveItemFieldFilter filter) {
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
            columns.append(", d.").append(field.columnName());
        }
        return columns.toString();
    }

    private List<Map<String, @Nullable Object>> normalizeDynamicFieldValues(
            List<Map<String, @Nullable Object>> rows, List<ArchiveFieldDto> fields) {
        if (rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(
                        row -> {
                            Map<String, @Nullable Object> normalized = new LinkedHashMap<>(row);
                            for (ArchiveFieldDto field : fields) {
                                normalized.compute(
                                        field.columnName(),
                                        (_, value) -> normalizeDynamicFieldValue(field, value));
                            }
                            return normalized;
                        })
                .toList();
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

    private void insertDynamicRecord(
            String tableName,
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> convertedDynamicFields) {
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
            List<ArchiveFieldDto> fields, Map<String, @Nullable Object> convertedDynamicFields) {
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

    private Map<String, @Nullable Object> convertDynamicFields(
            List<ArchiveFieldDto> fields, Map<String, @Nullable Object> dynamicFields) {
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

        Map<String, @Nullable Object> converted = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            converted.put(
                    field.fieldCode(), convertValue(field, dynamicFields.get(field.fieldCode())));
        }
        return converted;
    }

    private @Nullable Object convertValue(ArchiveFieldDto field, @Nullable Object value) {
        if (value == null || (value instanceof String text && StringUtils.isBlank(text))) {
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
        String text = value.toString();
        if (field.textLength() != null && text.length() > field.textLength()) {
            String message = field.fieldName() + "长度不能超过 " + field.textLength();
            throw badRequest(message, "dynamicFields." + field.fieldCode(), message);
        }
        return text;
    }

    private void insertItemAudit(
            String operationType, ArchiveItemDto record, String operationReason, Long operatedBy) {
        archiveMapper.insertItemAudit(
                "am_archive_item",
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

    private @Nullable String string(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private boolean bool(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private @Nullable Long longOrNull(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
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

    public record ArchiveItemQueryRequest(
            @Nullable Long categoryId,
            @Nullable String fondsCode,
            @Nullable String keyword,
            @Nullable Map<String, @Nullable Object> exactFilters,
            @Nullable List<@Nullable ArchiveItemFieldFilter> filters,
            @Nullable Integer limit,
            @Nullable String cursor,
            @Nullable List<@Nullable ArchiveItemOrderBy> orderBy) {}

    public record ArchiveItemOrderBy(@Nullable String field, @Nullable String direction) {}

    public record ArchiveItemFieldFilter(
            @Nullable String fieldCode,
            @Nullable Object value,
            @Nullable Object startValue,
            @Nullable Object endValue) {}

    public record ArchiveItemRequest(
            @Nullable Long categoryId,
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable String electronicStatus,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record ArchiveItemUpdateRequest(
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable String electronicStatus,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record DeleteItemRequest(@Nullable String reason) {}

    public record LockItemRequest(@Nullable String reason) {}

    public record ArchiveItemRelationRequest(
            @Nullable Long targetItemId,
            @Nullable String relationType,
            @Nullable String remark,
            @Nullable Integer sortOrder) {}

    public record ArchiveItemRelationTargetDto(
            Long itemId,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            @Nullable String archiveNo) {}

    public record ArchiveItemRelationDto(
            Long id,
            Long sourceItemId,
            Long targetItemId,
            String relationType,
            @Nullable String remark,
            int sortOrder,
            ArchiveItemRelationTargetDto target) {}

    public record ArchiveItemDto(
            Long id,
            @Nullable Long volumeId,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            @Nullable String archiveNo,
            String electronicStatus,
            int archiveYear,
            boolean lockedFlag,
            @Nullable String lockReason,
            @Nullable Long lockedBy,
            @Nullable LocalDateTime lockedAt) {}

    public record ArchiveItemListDto(
            @Nullable ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            boolean tableBuilt,
            @Nullable String self,
            @Nullable String prev,
            @Nullable String next,
            @Nullable String first,
            List<Map<String, @Nullable Object>> items) {}

    private record Cursor(String direction, List<Object> values) {}

    public record ArchiveItemDetailDto(
            ArchiveItemDto item,
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> dynamicFields,
            List<ArchiveFieldDto> physicalFields,
            Map<String, @Nullable Object> physicalFieldValues) {}

    public record SearchProjectionRebuildResult(Long categoryId, int rebuiltCount) {}
}
