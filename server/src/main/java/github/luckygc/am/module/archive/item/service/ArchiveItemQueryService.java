package github.luckygc.am.module.archive.item.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.page.PageRequest.Cursor;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.item.ArchiveItemRelationDirection;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;
import github.luckygc.am.module.archive.mapper.ArchiveSqlRelatedGroup;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintFieldDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemQueryService {

    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveDataScopeService dataScopeService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemSearchCriteriaCompiler criteriaCompiler;
    private final ArchiveItemCursorPageAssembler pageAssembler;

    public ArchiveItemQueryService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveMapper archiveMapper,
            ArchiveDataScopeService dataScopeService,
            AuthorizationPermissionService permissionService,
            ArchiveItemSearchCriteriaCompiler criteriaCompiler,
            ArchiveItemCursorPageAssembler pageAssembler) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveMapper = archiveMapper;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
        this.criteriaCompiler = criteriaCompiler;
        this.pageAssembler = pageAssembler;
    }

    public ArchiveItemListDto listItems(
            @Nullable Long categoryId, @Nullable String fondsCode, Long userId) {
        SearchArchiveItemsRequest request =
                new SearchArchiveItemsRequest(
                        categoryId, fondsCode, null, null, null, null, null, null);
        return queryItems(request, userId, false, false, pageRequest(request));
    }

    public ArchiveItemListDto searchItems(
            @Nullable SearchArchiveItemsRequest request, Long userId) {
        return queryItems(request, userId, false, false, pageRequest(request));
    }

    public ArchiveItemListDto searchItems(
            @Nullable SearchArchiveItemsRequest request, Long userId, PageRequest pageRequest) {
        return queryItems(request, userId, false, false, pageRequest);
    }

    public ArchiveItemListDto discoverItems(
            @Nullable SearchArchiveItemsRequest request, Long userId) {
        return queryItems(request, userId, true, false, pageRequest(request));
    }

    public ArchiveItemListDto discoverItems(
            @Nullable SearchArchiveItemsRequest request, Long userId, PageRequest pageRequest) {
        return queryItems(request, userId, true, false, pageRequest);
    }

    public ArchiveItemListDto searchDeletedItems(
            @Nullable SearchArchiveItemsRequest request, Long userId) {
        return queryItems(request, userId, false, true, pageRequest(request));
    }

    public ArchiveItemListDto searchDeletedItems(
            @Nullable SearchArchiveItemsRequest request, Long userId, PageRequest pageRequest) {
        return queryItems(request, userId, false, true, pageRequest);
    }

    public List<ArchiveRelatedFilterCategoryDto> listRelatedFilterCategories(Long categoryId) {
        archiveCategoryService.getCategory(categoryId);
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
            boolean deleted,
            PageRequest pageRequest) {
        if (request != null && request.volumeId() != null && request.volumeId() <= 0) {
            throw badRequest("案卷 ID 必须为正数", "volumeId", "案卷 ID 必须为正数");
        }
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
                return itemList(null, List.of(), emptyPage(pageRequest, 0L));
            }
            int limit = pageRequest.size();
            List<Map<String, @Nullable Object>> rows =
                    archiveMapper.listItemOverview().stream().limit(limit).toList();
            return itemList(null, List.of(), rows);
        }

        ArchiveCategoryDto category = archiveCategoryService.getCategory(request.categoryId());
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
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "档案分类动态表未创建");
        }
        List<String> indexedFieldCodes = indexedFieldCodes(request.categoryId(), archiveLevel);
        List<ArchiveSqlOrder> orderBy =
                deleted ? deletedOrderBy() : orderBy(request.orderBy(), fields, indexedFieldCodes);
        Cursor cursor = cursor(pageRequest);
        List<ArchiveSqlCondition> conditions =
                criteriaCompiler.buildSearchConditions(
                        request.categoryId(), archiveLevel, fields, request.where());
        List<ArchiveSqlRelatedGroup> relatedGroups =
                criteriaCompiler.buildRelatedGroups(request.relatedGroups(), userId);
        String requestedFondsCode = StringUtils.trimToNull(request.fondsCode());
        ArchiveDataScopeFilter dataScopeFilter =
                dataScopeService.buildItemFilter(userId, request.categoryId(), requestedFondsCode);
        if (dataScopeFilter.empty()) {
            return itemList(category, fields, emptyPage(pageRequest, 0L));
        }
        CursoredPage<Map<String, @Nullable Object>> itemPage =
                pageAssembler.queryDynamicItemPage(
                        pageRequest,
                        tableName,
                        visibleFields,
                        deleted,
                        requestedFondsCode,
                        request.volumeId(),
                        dataScopeFilter.allData() ? List.of() : dataScopeFilter.groups(),
                        conditions,
                        relatedGroups,
                        keyword,
                        orderBy,
                        cursor);
        CursorPageResponse<Map<String, @Nullable Object>> page =
                CursorPageResponse.from(itemPage, pageRequest, item -> item);
        return itemList(category, fields, page);
    }

    private PageRequest pageRequest(@Nullable SearchArchiveItemsRequest request) {
        return CursorPageTokenCodec.pageRequest(
                pageLimit(request == null ? null : request.limit()),
                request == null ? null : request.cursor(),
                false);
    }

    private ArchiveItemListDto itemList(
            @Nullable ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            List<Map<String, @Nullable Object>> items) {
        return itemList(
                category,
                fields,
                CursorPageResponse.withCursorValues(items, 0, null, null, null, null, null));
    }

    private ArchiveItemListDto itemList(
            @Nullable ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            CursorPageResponse<Map<String, @Nullable Object>> page) {
        return new ArchiveItemListDto(category, fields, page);
    }

    private CursorPageResponse<Map<String, @Nullable Object>> emptyPage(
            PageRequest pageRequest, @Nullable Long total) {
        Long responseTotal = pageRequest.requestTotal() ? total : null;
        return CursorPageResponse.<Map<String, @Nullable Object>>withCursorValues(
                List.of(), pageRequest.size(), null, null, null, null, responseTotal);
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

    private @Nullable Cursor cursor(PageRequest pageRequest) {
        return switch (pageRequest.mode()) {
            case CURSOR_NEXT, CURSOR_PREVIOUS -> pageRequest.cursor().orElseThrow();
            case OFFSET -> null;
        };
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

    public record SearchArchiveItemsRequest(
            @Nullable Long categoryId,
            @Nullable String fondsCode,
            @Nullable String keyword,
            @Nullable ArchiveItemWhere where,
            @Nullable List<@Nullable ArchiveItemRelatedGroup> relatedGroups,
            @Nullable Integer limit,
            @Nullable String cursor,
            @Nullable List<@Nullable ArchiveItemOrderBy> orderBy,
            @Nullable Long volumeId) {

        public SearchArchiveItemsRequest(
                @Nullable Long categoryId,
                @Nullable String fondsCode,
                @Nullable String keyword,
                @Nullable ArchiveItemWhere where,
                @Nullable List<@Nullable ArchiveItemRelatedGroup> relatedGroups,
                @Nullable Integer limit,
                @Nullable String cursor,
                @Nullable List<@Nullable ArchiveItemOrderBy> orderBy) {
            this(
                    categoryId,
                    fondsCode,
                    keyword,
                    where,
                    relatedGroups,
                    limit,
                    cursor,
                    orderBy,
                    null);
        }

        public SearchArchiveItemsRequest withPage(
                @Nullable Integer limit, @Nullable String cursor) {
            return new SearchArchiveItemsRequest(
                    categoryId,
                    fondsCode,
                    keyword,
                    where,
                    relatedGroups,
                    limit,
                    cursor,
                    orderBy,
                    volumeId);
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

    public record ArchiveItemListDto(
            @Nullable ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            @JsonIgnore CursorPageResponse<Map<String, @Nullable Object>> page)
            implements CursorPageResponse<Map<String, @Nullable Object>> {

        @Override
        @JsonProperty("items")
        public List<Map<String, @Nullable Object>> items() {
            return page.items();
        }

        @Override
        @JsonProperty("self")
        public @Nullable String self() {
            return page.self();
        }

        @Override
        @JsonProperty("prev")
        public @Nullable String prev() {
            return page.prev();
        }

        @Override
        @JsonProperty("next")
        public @Nullable String next() {
            return page.next();
        }

        @Override
        @JsonProperty("first")
        public @Nullable String first() {
            return page.first();
        }

        @Override
        @JsonProperty("total")
        public @Nullable Long total() {
            return page.total();
        }

        @Override
        public ArchiveItemListDto encodeCursorTokens(CursorPageTokenContext context) {
            return new ArchiveItemListDto(category, fields, page.encodeCursorTokens(context));
        }
    }
}
