package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.mapper.ArchiveItemRelationCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveItemRelationCriteria.ArchiveItemRelationPageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveItemRelationCriteria.ArchiveItemRelationTargetScope;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemRelationService {

    private final ArchiveMapper archiveMapper;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveCategoryService archiveCategoryService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemRelationService(
            ArchiveMapper archiveMapper,
            ArchiveItemReadService archiveItemReadService,
            ArchiveDataScopeService dataScopeService,
            ArchiveCategoryService archiveCategoryService,
            AuthorizationPermissionService permissionService) {
        this.archiveMapper = archiveMapper;
        this.archiveItemReadService = archiveItemReadService;
        this.dataScopeService = dataScopeService;
        this.archiveCategoryService = archiveCategoryService;
        this.permissionService = permissionService;
    }

    public CursorPageResponse<ArchiveItemRelationResponse> listRelations(
            Long itemId, @Nullable Integer depth, PageRequest pageRequest, Long userId) {
        requirePermission(userId, "archive:item:read");
        normalizeDepth(depth);
        archiveItemReadService.assertItemInDataScope(itemId, userId);
        boolean requestTotal = shouldRequestTotal(pageRequest);
        ArchiveItemRelationCriteria criteria = relationCriteria(userId, requestTotal);
        ArchiveItemRelationPageWindow pageWindow = pageWindow(pageRequest);
        List<Map<String, Object>> rows =
                archiveMapper.listItemRelations(itemId, criteria, pageWindow);
        return toCursorPage(rows, pageRequest, requestTotal);
    }

    @Transactional
    public ArchiveItemRelationResponse createRelation(
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
        archiveItemReadService.assertItemInDataScope(itemId, userId);
        archiveItemReadService.assertItemInDataScope(request.targetItemId(), userId);
        Long relationId;
        try {
            relationId = archiveMapper.insertItemRelation(itemId, request.targetItemId());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联档案已存在", exception);
        }
        Map<String, Object> row = archiveMapper.getItemRelation(relationId, itemId);
        if (row == null) {
            throw new IllegalStateException("关联档案创建后不可见");
        }
        return toRelationResponse(row);
    }

    @Transactional
    public void deleteRelation(Long itemId, @Nullable Long relationId, Long userId) {
        requirePermission(userId, "archive:item:update");
        if (relationId == null || relationId <= 0) {
            throw badRequest("关联 ID 不合法", "relationId", "关联 ID 必须为正数");
        }
        archiveItemReadService.assertItemInDataScope(itemId, userId);
        Map<String, Object> relation = archiveMapper.getItemRelation(relationId, itemId);
        if (relation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在");
        }
        archiveItemReadService.assertItemInDataScope(
                number(relation, "relatedItemId").longValue(), userId);
        if (archiveMapper.deleteItemRelation(relationId, itemId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在");
        }
    }

    private ArchiveItemRelationCriteria relationCriteria(Long userId, boolean requestTotal) {
        List<ArchiveItemRelationTargetScope> targetScopes = new ArrayList<>();
        for (var category : archiveCategoryService.listCategories(null)) {
            ArchiveDataScopeFilter filter =
                    dataScopeService.buildItemFilter(userId, category.id(), null);
            if (filter.allData()) {
                return new ArchiveItemRelationCriteria(true, List.of(), requestTotal);
            }
            if (filter.empty()) {
                continue;
            }
            targetScopes.add(
                    new ArchiveItemRelationTargetScope(
                            category.categoryCode(),
                            ArchiveDynamicTableNames.tableName(category, ArchiveLevel.ITEM),
                            filter.groups()));
        }
        return new ArchiveItemRelationCriteria(false, targetScopes, requestTotal);
    }

    private ArchiveItemRelationPageWindow pageWindow(PageRequest pageRequest) {
        @Nullable Long cursorId = null;
        if (pageRequest.cursor().isPresent()) {
            List<?> values = pageRequest.cursor().orElseThrow().elements();
            if (values.size() != 1 || !(values.getFirst() instanceof Number number)) {
                throw badRequest("分页 cursor 无效", "cursor", "关系 cursor 必须包含一个数值 ID");
            }
            cursorId = number.longValue();
        }
        return new ArchiveItemRelationPageWindow(
                pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS,
                cursorId,
                pageRequest.size() + 1);
    }

    private CursorPageResponse<ArchiveItemRelationResponse> toCursorPage(
            List<Map<String, Object>> rows, PageRequest pageRequest, boolean requestTotal) {
        int limit = pageRequest.size();
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> pageRows =
                hasMore ? new ArrayList<>(rows.subList(0, limit)) : new ArrayList<>(rows);
        boolean previousQuery = pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS;
        if (previousQuery) {
            pageRows = pageRows.reversed();
        }
        List<ArchiveItemRelationResponse> items =
                pageRows.stream().map(this::toRelationResponse).toList();
        boolean hasPrevious = previousQuery ? hasMore : pageRequest.cursor().isPresent();
        boolean hasNext = previousQuery ? pageRequest.cursor().isPresent() : hasMore;
        List<Long> self =
                pageRows.isEmpty() ? null : List.of(number(pageRows.getFirst(), "id").longValue());
        List<Long> prev =
                hasPrevious && !pageRows.isEmpty()
                        ? List.of(number(pageRows.getFirst(), "id").longValue())
                        : null;
        List<Long> next =
                hasNext && !pageRows.isEmpty()
                        ? List.of(number(pageRows.getLast(), "id").longValue())
                        : null;
        Long total =
                requestTotal
                        ? (pageRows.isEmpty()
                                ? 0L
                                : number(pageRows.getFirst(), "total").longValue())
                        : null;
        return CursorPageResponse.withCursorValues(items, limit, self, prev, next, null, total);
    }

    private boolean shouldRequestTotal(PageRequest pageRequest) {
        return pageRequest.requestTotal() && pageRequest.cursor().isEmpty();
    }

    private ArchiveItemRelationResponse toRelationResponse(Map<String, ?> row) {
        return new ArchiveItemRelationResponse(
                number(row, "id").longValue(),
                number(row, "sourceItemId").longValue(),
                number(row, "targetItemId").longValue(),
                number(row, "relatedItemId").longValue(),
                string(row, "direction"),
                dateTime(row, "createdAt"),
                new ArchiveItemRelationTargetResponse(
                        number(row, "relatedItemId").longValue(),
                        string(row, "relatedFondsCode"),
                        string(row, "relatedFondsName"),
                        string(row, "relatedCategoryCode"),
                        string(row, "relatedCategoryName"),
                        string(row, "relatedArchiveNo")));
    }

    private int normalizeDepth(@Nullable Integer depth) {
        int normalizedDepth = depth == null ? 1 : depth;
        if (normalizedDepth < 1 || normalizedDepth > 2) {
            throw badRequest("关联档案展开层级必须在 1 到 2 之间", "depth", "关联档案展开层级必须在 1 到 2 之间");
        }
        return normalizedDepth;
    }

    private void requirePermission(Long userId, String permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }

    private @Nullable String string(Map<String, ?> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Number number(Map<String, ?> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private @Nullable LocalDateTime dateTime(Map<String, ?> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private @Nullable Object value(Map<String, ?> row, String key) {
        return row.containsKey(key)
                ? row.get(key)
                : row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveItemRelationRequest(@Nullable Long targetItemId) {}

    public record ArchiveItemRelationTargetResponse(
            Long itemId,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            @Nullable String archiveNo) {}

    public record ArchiveItemRelationResponse(
            Long id,
            Long sourceItemId,
            Long targetItemId,
            Long relatedItemId,
            String direction,
            @Nullable LocalDateTime createdAt,
            ArchiveItemRelationTargetResponse relatedItem) {}
}
