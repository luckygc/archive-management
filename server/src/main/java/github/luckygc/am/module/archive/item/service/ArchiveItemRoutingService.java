package github.luckygc.am.module.archive.item.service;

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
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.item.ArchiveItemRelationDirection;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;
import github.luckygc.am.module.archive.mapper.ArchiveSqlRelatedGroup;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintFieldDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

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
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveGovernanceService governanceService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveItemSearchProjectionService searchProjectionService;
    private final ArchiveDataScopeService dataScopeService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemAuditDataRepository auditRepository;

    public ArchiveItemRoutingService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveGovernanceService governanceService,
            ArchiveMapper archiveMapper,
            ArchiveItemSearchProjectionService searchProjectionService,
            ArchiveDataScopeService dataScopeService,
            AuthorizationPermissionService permissionService,
            ArchiveItemAuditDataRepository auditRepository) {
        this.archiveMetadataService = archiveMetadataService;
        this.governanceService = governanceService;
        this.archiveMapper = archiveMapper;
        this.searchProjectionService = searchProjectionService;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
        this.auditRepository = auditRepository;
    }

    public ArchiveItemListDto listItems(
            @Nullable Long categoryId, @Nullable String fondsCode, Long userId) {
        return searchItems(
                new SearchArchiveItemsRequest(
                        categoryId, fondsCode, null, null, null, null, null, null),
                userId);
    }

    public ArchiveItemListDto searchItems(
            @Nullable SearchArchiveItemsRequest request, Long userId) {
        return searchItems(request, userId, null);
    }

    public ArchiveItemListDto searchItems(
            @Nullable SearchArchiveItemsRequest request,
            Long userId,
            @Nullable CursorPageTokenContext cursorContext) {
        return queryItems(request, userId, false, false, false, cursorContext);
    }

    public ArchiveItemListDto discoverItems(
            @Nullable SearchArchiveItemsRequest request, Long userId) {
        return discoverItems(request, userId, null);
    }

    public ArchiveItemListDto discoverItems(
            @Nullable SearchArchiveItemsRequest request,
            Long userId,
            @Nullable CursorPageTokenContext cursorContext) {
        return queryItems(request, userId, true, true, false, cursorContext);
    }

    public ArchiveItemListDto searchDeletedItems(
            @Nullable SearchArchiveItemsRequest request, Long userId) {
        return searchDeletedItems(request, userId, null);
    }

    public ArchiveItemListDto searchDeletedItems(
            @Nullable SearchArchiveItemsRequest request,
            Long userId,
            @Nullable CursorPageTokenContext cursorContext) {
        return queryItems(request, userId, false, false, true, cursorContext);
    }

    public List<ArchiveRelatedFilterCategoryDto> listRelatedFilterCategories(Long categoryId) {
        archiveMetadataService.getCategory(categoryId);
        return archiveMapper.listRelatedFilterCategories(categoryId).stream()
                .map(
                        row ->
                                new ArchiveRelatedFilterCategoryDto(
                                        number(row, "relatedCategoryId").longValue(),
                                        string(row, "relatedCategoryCode"),
                                        string(row, "relatedCategoryName"),
                                        ArchiveItemRelationDirection.fromValue(
                                                string(row, "direction"))))
                .toList();
    }

    private ArchiveItemListDto queryItems(
            @Nullable SearchArchiveItemsRequest request,
            Long userId,
            boolean allowKeyword,
            boolean requireAuthenticatedUser,
            boolean deleted,
            @Nullable CursorPageTokenContext cursorContext) {
        requirePermission(userId, "archive:item:read");
        String keyword = StringUtils.trimToNull(request == null ? null : request.keyword());
        if (StringUtils.isNotBlank(keyword) && !allowKeyword) {
            throw badRequest(
                    "档案管理列表不支持全文关键词检索", "keyword", "档案管理列表只支持数据库字段筛选；全文检索用于查档、借阅等普通用户业务入口");
        }
        if (request == null || request.categoryId() == null) {
            if (StringUtils.isNotBlank(keyword)) {
                throw badRequest("全文检索必须选择档案分类", "categoryId", "全文检索必须选择档案分类");
            }
            if (!dataScopeService.resolveUserDataScope(userId).allData()) {
                return new ArchiveItemListDto(
                        null, List.of(), true, null, null, null, null, List.of());
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
        List<String> indexedFieldCodes = indexedFieldCodes(request.categoryId(), archiveLevel);
        List<ArchiveSqlOrder> orderBy =
                deleted ? deletedOrderBy() : orderBy(request.orderBy(), fields, indexedFieldCodes);
        Cursor cursor = decodeCursor(request.cursor());
        List<ArchiveSqlCondition> conditions =
                buildSearchConditions(request.categoryId(), archiveLevel, fields, request.where());
        List<ArchiveSqlRelatedGroup> relatedGroups =
                buildRelatedGroups(request.relatedGroups(), userId);
        ArchiveDataScopeFilter dataScopeFilter =
                dataScopeService.buildItemFilter(
                        userId, request.categoryId(), StringUtils.trimToNull(request.fondsCode()));
        if (dataScopeFilter.empty()) {
            return new ArchiveItemListDto(
                    category, fields, true, null, null, null, null, List.of());
        }
        List<ArchiveSqlOrder> queryOrderBy =
                cursor != null && "prev".equals(cursor.direction()) ? invert(orderBy) : orderBy;
        List<Map<String, @Nullable Object>> rows =
                archiveMapper.listDynamicItems(
                        tableName,
                        selectColumns(visibleFields),
                        archiveLevel.value(),
                        deleted,
                        StringUtils.trimToNull(request.fondsCode()),
                        dataScopeFilter.allData() ? List.of() : dataScopeFilter.groups(),
                        conditions,
                        relatedGroups,
                        keyword,
                        userId,
                        requireAuthenticatedUser,
                        orderBySql(queryOrderBy),
                        cursorPredicateSql(queryOrderBy, cursor),
                        cursorValues(cursor),
                        limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Map<String, @Nullable Object>> rawPageItems = hasMore ? rows.subList(0, limit) : rows;
        if (cursor != null && "prev".equals(cursor.direction())) {
            rawPageItems = rawPageItems.reversed();
        }
        List<Map<String, @Nullable Object>> pageItems =
                normalizeDynamicFieldValues(rawPageItems, visibleFields);
        CursorPageTokenContext tokenContext =
                cursorContext == null ? new CursorPageTokenContext("", "") : cursorContext;
        String prev =
                cursor == null || rawPageItems.isEmpty()
                        ? null
                        : encodeCursor(
                                "prev", tokenContext, limit, orderBy, rawPageItems.getFirst());
        String next =
                rawPageItems.isEmpty()
                                || (cursor != null && "prev".equals(cursor.direction()) && !hasMore)
                        ? null
                        : encodeCursor(
                                "next", tokenContext, limit, orderBy, rawPageItems.getLast());
        if ((cursor == null || "next".equals(cursor.direction())) && !hasMore) {
            next = null;
        }
        return new ArchiveItemListDto(
                category,
                fields,
                true,
                encodeCursor(
                        "self",
                        tokenContext,
                        limit,
                        orderBy,
                        rawPageItems.isEmpty() ? null : rawPageItems.getFirst()),
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
            @Nullable List<@Nullable ArchiveItemOrderBy> requestOrderBy,
            List<ArchiveFieldDto> fields,
            List<String> indexedFieldCodes) {
        List<ArchiveSqlOrder> orders = new ArrayList<>();
        if (requestOrderBy != null) {
            Map<String, ArchiveFieldDto> fieldsByCode =
                    fields.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            ArchiveFieldDto::fieldCode, field -> field));
            for (ArchiveItemOrderBy order : requestOrderBy) {
                orders.add(toSqlOrder(order, fieldsByCode, indexedFieldCodes));
            }
        }
        appendFallbackOrder(orders, new ArchiveSqlOrder("i.created_at", Direction.DESC));
        appendFallbackOrder(orders, new ArchiveSqlOrder("i.id", Direction.DESC));
        return orders;
    }

    private List<ArchiveSqlOrder> deletedOrderBy() {
        return List.of(
                new ArchiveSqlOrder("i.deleted_at", Direction.DESC),
                new ArchiveSqlOrder("i.id", Direction.DESC));
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

    private ArchiveSqlOrder toSqlOrder(
            @Nullable ArchiveItemOrderBy order,
            Map<String, ArchiveFieldDto> fieldsByCode,
            List<String> indexedFieldCodes) {
        String field = order == null ? null : StringUtils.trimToNull(order.field());
        if (field == null) {
            throw badRequest("排序字段不能为空", "orderBy.field", "排序字段不能为空");
        }
        Direction direction =
                "ASC".equalsIgnoreCase(order.direction()) ? Direction.ASC : Direction.DESC;
        return new ArchiveSqlOrder(
                sortExpression(field, fieldsByCode, indexedFieldCodes), direction);
    }

    private String sortExpression(
            String field,
            Map<String, ArchiveFieldDto> fieldsByCode,
            List<String> indexedFieldCodes) {
        String fixedExpression =
                switch (field) {
                    case "createdAt" -> "i.created_at";
                    case "archiveNo" -> "i.archive_no";
                    case "archiveYear" -> "i.archive_year";
                    case "fondsCode" -> "i.fonds_code";
                    case "categoryCode" -> "i.category_code";
                    case "electronicStatus" -> "i.electronic_status";
                    case "id" -> "i.id";
                    default -> null;
                };
        if (fixedExpression != null) {
            return fixedExpression;
        }
        ArchiveFieldDto dynamicField = fieldsByCode.get(field);
        if (dynamicField != null
                && (dynamicField.exactSearchable()
                        || indexedFieldCodes.contains(dynamicField.fieldCode()))) {
            return "d." + dynamicField.columnName();
        }
        throw badRequest("不支持的排序字段", "orderBy.field", "不支持的排序字段：" + field);
    }

    private List<String> indexedFieldCodes(Long categoryId, ArchiveLevel archiveLevel) {
        return archiveMetadataService.listUniqueConstraints(categoryId).stream()
                .filter(ArchiveUniqueConstraintDto::enabled)
                .filter(constraint -> constraint.archiveLevel() == archiveLevel)
                .flatMap(constraint -> constraint.fields().stream())
                .map(ArchiveUniqueConstraintFieldDto::fieldCode)
                .distinct()
                .toList();
    }

    private String orderBySql(List<ArchiveSqlOrder> orders) {
        return orders.stream()
                .map(ArchiveSqlOrder::sql)
                .reduce((left, right) -> left + ", " + right)
                .orElse("i.created_at desc, i.id desc");
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

    private @Nullable Cursor decodeCursor(@Nullable String token) {
        CursorPageTokenCodec.DecodedCursor decoded = CursorPageTokenCodec.decode(token);
        if (decoded == null) {
            return null;
        }
        return new Cursor(decoded.direction(), decoded.values());
    }

    private @Nullable String encodeCursor(
            String direction,
            CursorPageTokenContext context,
            int limit,
            List<ArchiveSqlOrder> orders,
            @Nullable Map<String, @Nullable Object> row) {
        if (row == null) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        for (ArchiveSqlOrder order : orders) {
            values.add(cursorRowValue(row, order.expression()));
        }
        return CursorPageTokenCodec.encode(direction, values, limit, context);
    }

    private @Nullable Object cursorRowValue(Map<String, @Nullable Object> row, String expression) {
        return switch (expression) {
            case "i.created_at" -> value(row, "createdAt");
            case "i.deleted_at" -> value(row, "deletedAt");
            case "i.id" -> longCursorValue(row, "id");
            case "i.archive_no" -> value(row, "archiveNo");
            case "i.archive_year" -> integerCursorValue(row, "archiveYear");
            case "i.fonds_code" -> value(row, "fondsCode");
            case "i.category_code" -> value(row, "categoryCode");
            case "i.electronic_status" -> value(row, "electronicStatus");
            default -> dynamicCursorRowValue(row, expression);
        };
    }

    private @Nullable Object dynamicCursorRowValue(
            Map<String, @Nullable Object> row, String expression) {
        if (!expression.startsWith("d.")) {
            throw new IllegalStateException("不支持的 cursor 排序字段：" + expression);
        }
        return value(row, expression.substring(2));
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

    @Transactional
    public ArchiveItemDto createItem(@Nullable CreateArchiveItemRequest request, Long userId) {
        requirePermission(userId, "archive:item:create");
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
        ArchiveFondsDto fonds = archiveMetadataService.getEnabledFondsByCode(request.fondsCode());
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
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        ensureItemArchiveNoUnique(category.categoryCode(), archiveNo, null);
        Map<String, @Nullable Object> convertedDynamicFields =
                convertDynamicFields(fields, dynamicFields);
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : convertDynamicFields(physicalFields, requestPhysicalFields);
        assertProposedItemInDataScope(
                userId,
                category,
                fonds.fondsCode(),
                request.securityLevelId(),
                request.retentionPeriodId(),
                fields,
                convertedDynamicFields);
        Long governanceSchemeVersionId =
                governanceService
                        .requireDefaultVersionForNewArchive(
                                fonds.fondsCode(), category.categoryCode())
                        .getId();

        Long recordId;
        try {
            recordId =
                    archiveMapper.insertArchiveItem(
                            archiveLevel.value(),
                            volumeId,
                            fonds.fondsCode(),
                            fonds.fondsName(),
                            category.categoryCode(),
                            category.categoryName(),
                            archiveNo,
                            StringUtils.defaultIfBlank(request.electronicStatus(), "DRAFT"),
                            request.securityLevelId(),
                            request.retentionPeriodId(),
                            archiveYear,
                            governanceSchemeVersionId,
                            userId);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
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

    public void assertItemInDataScope(Long id, Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveItemDto record = getItem(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        assertItemInDataScope(userId, category, record);
    }

    public ArchiveItemDetailDto getItemDetail(
            Long id, Long userId, @Nullable ArchiveLayoutSurface surface) {
        requirePermission(userId, "archive:item:read");
        ArchiveItemDto record = getItem(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        assertItemInDataScope(userId, category, record);
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
            Long id, @Nullable UpdateArchiveItemRequest request, Long userId) {
        requirePermission(userId, "archive:item:update");
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        ArchiveItemDetailDto before = getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        assertItemInDataScope(userId, before.category(), before.item());
        ensureItemEditable(before.item());
        ArchiveCategoryDto category = before.category();
        String tableName = dynamicTableName(category, ArchiveLevel.ITEM);
        if (!isDynamicTableBuilt(category, ArchiveLevel.ITEM)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getEnabledFondsByCode(request.fondsCode());
        Long volumeId =
                validateParentForWrite(
                        ArchiveLevel.ITEM,
                        request.volumeId() == null ? before.item().volumeId() : request.volumeId(),
                        before.item().categoryCode(),
                        fonds.fondsCode());
        int archiveYear =
                request.archiveYear() == null ? before.item().archiveYear() : request.archiveYear();
        validateArchiveYear(archiveYear);
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        ensureItemArchiveNoUnique(before.item().categoryCode(), archiveNo, id);
        Map<String, @Nullable Object> requestDynamicFields =
                request.dynamicFields() == null ? before.dynamicFields() : request.dynamicFields();
        Map<String, @Nullable Object> convertedDynamicFields =
                convertDynamicFields(before.fields(), requestDynamicFields);
        Map<String, @Nullable Object> requestPhysicalFields = request.physicalFields();
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : convertDynamicFields(before.physicalFields(), requestPhysicalFields);
        assertProposedItemInDataScope(
                userId,
                category,
                fonds.fondsCode(),
                request.securityLevelId() == null
                        ? before.item().securityLevelId()
                        : request.securityLevelId(),
                request.retentionPeriodId() == null
                        ? before.item().retentionPeriodId()
                        : request.retentionPeriodId(),
                before.fields(),
                convertedDynamicFields);
        int updated;
        try {
            updated =
                    archiveMapper.updateArchiveItem(
                            id,
                            volumeId,
                            fonds.fondsCode(),
                            fonds.fondsName(),
                            archiveNo,
                            StringUtils.defaultIfBlank(
                                    request.electronicStatus(), before.item().electronicStatus()),
                            request.securityLevelId() == null
                                    ? before.item().securityLevelId()
                                    : request.securityLevelId(),
                            request.retentionPeriodId() == null
                                    ? before.item().retentionPeriodId()
                                    : request.retentionPeriodId(),
                            archiveYear,
                            userId);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
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
    public void deleteItem(Long id, Long userId, @Nullable DeleteItemRequest request) {
        requirePermission(userId, "archive:item:delete");
        ArchiveItemDto record = getItem(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        assertItemInDataScope(userId, category, record);
        ensureItemEditable(record);
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
    public ArchiveItemDto lockItem(Long id, Long userId, @Nullable LockItemRequest request) {
        requirePermission(userId, "archive:item:lock");
        if (id == null || id <= 0) {
            throw badRequest("档案条目 ID 不合法");
        }
        ArchiveItemDto before = getItem(id);
        ArchiveCategoryDto category = getCategoryByCode(before.categoryCode());
        assertItemInDataScope(userId, category, before);
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
    public ArchiveItemDto unlockItem(Long id, Long userId) {
        requirePermission(userId, "archive:item:lock");
        if (id == null || id <= 0) {
            throw badRequest("档案条目 ID 不合法");
        }
        ArchiveItemDto before = getItem(id);
        ArchiveCategoryDto category = getCategoryByCode(before.categoryCode());
        assertItemInDataScope(userId, category, before);
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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
    }

    public List<ArchiveItemRelationDto> listRelations(
            Long itemId, @Nullable Integer depth, Long userId) {
        requirePermission(userId, "archive:item:read");
        int normalizedDepth = depth == null ? 1 : depth;
        if (normalizedDepth < 1 || normalizedDepth > 2) {
            throw badRequest("关联档案展开层级必须在 1 到 2 之间", "depth", "关联档案展开层级必须在 1 到 2 之间");
        }
        assertItemInDataScope(itemId, userId);
        return archiveMapper.listItemRelations(itemId).stream()
                .peek(row -> assertItemInDataScope(number(row, "targetItemId").longValue(), userId))
                .map(this::toRelationDto)
                .toList();
    }

    @Transactional
    public ArchiveItemRelationDto createRelation(
            Long itemId, @Nullable ArchiveItemRelationRequest request, Long userId) {
        requirePermission(userId, "archive:item:update");
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (request.targetItemId() == null || request.targetItemId() <= 0) {
            throw badRequest("目标条目不能为空", "targetItemId", "目标条目不能为空");
        }
        if (itemId.equals(request.targetItemId())) {
            throw badRequest("不能关联自身", "targetItemId", "不能关联自身");
        }
        assertItemInDataScope(itemId, userId);
        assertItemInDataScope(request.targetItemId(), userId);
        try {
            archiveMapper.insertItemRelation(itemId, request.targetItemId());
        } catch (DuplicateKeyException exception) {
            throw badRequest("关联档案已存在", "targetItemId", "关联档案已存在");
        }
        return listRelations(itemId, 1, userId).stream()
                .filter(relation -> relation.targetItemId().equals(request.targetItemId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("关联档案创建后不可见"));
    }

    @Transactional
    public void deleteRelation(Long itemId, @Nullable Long relationId, Long userId) {
        requirePermission(userId, "archive:item:update");
        if (relationId == null || relationId <= 0) {
            throw badRequest("关联 ID 不合法");
        }
        assertItemInDataScope(itemId, userId);
        Long targetItemId =
                archiveMapper.listItemRelations(itemId).stream()
                        .filter(row -> relationId.equals(number(row, "id").longValue()))
                        .map(row -> number(row, "targetItemId").longValue())
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在"));
        assertItemInDataScope(targetItemId, userId);
        int updated = archiveMapper.deleteItemRelation(relationId, itemId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在");
        }
    }

    private ArchiveItemRelationDto toRelationDto(Map<String, @Nullable Object> row) {
        return new ArchiveItemRelationDto(
                number(row, "id").longValue(),
                number(row, "sourceItemId").longValue(),
                number(row, "targetItemId").longValue(),
                dateTime(row, "createdAt"),
                new ArchiveItemRelationTargetDto(
                        number(row, "targetItemId").longValue(),
                        string(row, "targetFondsCode"),
                        string(row, "targetFondsName"),
                        string(row, "targetCategoryCode"),
                        string(row, "targetCategoryName"),
                        string(row, "targetArchiveNo")));
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
                longOrNull(row, "securityLevelId"),
                longOrNull(row, "retentionPeriodId"),
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

    private List<ArchiveSqlRelatedGroup> buildRelatedGroups(
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
            ArchiveCategoryDto category = archiveMetadataService.getCategory(group.categoryId());
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

    private void ensureItemArchiveNoUnique(
            String categoryCode, @Nullable String archiveNo, @Nullable Long excludedId) {
        if (StringUtils.isBlank(archiveNo)) {
            return;
        }
        if (archiveMapper.countArchiveItemsByArchiveNo(categoryCode, archiveNo, excludedId) > 0) {
            throw duplicateArchiveNo();
        }
    }

    private BadRequestException duplicateArchiveNo() {
        return badRequest("档号已存在", "archiveNo", "档号已存在");
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

    private void insertItemAudit(
            String operationType, ArchiveItemDto record, String operationReason, Long operatedBy) {
        ArchiveItemAudit audit = new ArchiveItemAudit();
        audit.setSourceTableName("am_archive_item");
        audit.setSourceRecordId(record.id());
        audit.setArchiveItemId(record.id());
        audit.setFondsCode(record.fondsCode());
        audit.setCategoryCode(record.categoryCode());
        audit.setOperationType(operationType);
        audit.setOperationReason(operationReason);
        audit.setOperatedBy(operatedBy);
        auditRepository.insert(audit);
    }

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }

    private void requirePermission(Long userId, String permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private void assertItemInDataScope(
            Long userId, ArchiveCategoryDto category, ArchiveItemDto record) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), record.fondsCode());
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        Map<String, @Nullable Object> dynamicRecord = loadDynamicRecord(category, record.id());
        if (!dataScopeService.matchesItemFilter(
                filter,
                record.fondsCode(),
                record.securityLevelId(),
                record.retentionPeriodId(),
                dynamicRecord)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    private void assertProposedItemInDataScope(
            Long userId,
            ArchiveCategoryDto category,
            String fondsCode,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> dynamicFieldsByCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), fondsCode);
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        Map<String, @Nullable Object> dynamicRow = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            dynamicRow.put(field.columnName(), dynamicFieldsByCode.get(field.fieldCode()));
        }
        if (!dataScopeService.matchesItemFilter(
                filter, fondsCode, securityLevelId, retentionPeriodId, dynamicRow)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
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

    public record SearchArchiveItemsRequest(
            @Nullable Long categoryId,
            @Nullable String fondsCode,
            @Nullable String keyword,
            @Nullable ArchiveItemWhere where,
            @Nullable List<@Nullable ArchiveItemRelatedGroup> relatedGroups,
            @Nullable Integer limit,
            @Nullable String cursor,
            @Nullable List<@Nullable ArchiveItemOrderBy> orderBy) {

        public SearchArchiveItemsRequest withPage(
                @Nullable Integer limit, @Nullable String cursor) {
            return new SearchArchiveItemsRequest(
                    categoryId, fondsCode, keyword, where, relatedGroups, limit, cursor, orderBy);
        }
    }

    public record ArchiveItemOrderBy(@Nullable String field, @Nullable String direction) {}

    public record ArchiveItemWhere(
            @Nullable List<@Nullable ArchiveItemQueryCondition> conditions) {}

    public record ArchiveItemQueryCondition(
            @Nullable String fieldCode,
            @Nullable ArchiveItemQueryOperator op,
            @Nullable Object value,
            @Nullable Object startValue,
            @Nullable Object endValue) {}

    public record ArchiveItemRelatedGroup(
            @Nullable Long categoryId,
            @Nullable ArchiveItemRelationDirection direction,
            @Nullable ArchiveItemWhere where) {}

    public record ArchiveRelatedFilterCategoryDto(
            Long categoryId,
            String categoryCode,
            String categoryName,
            ArchiveItemRelationDirection direction) {}

    public record CreateArchiveItemRequest(
            @Nullable Long categoryId,
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record UpdateArchiveItemRequest(
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record DeleteItemRequest(@Nullable String reason) {}

    public record LockItemRequest(@Nullable String reason) {}

    public record ArchiveItemRelationRequest(@Nullable Long targetItemId) {}

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
            @Nullable LocalDateTime createdAt,
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
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
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
