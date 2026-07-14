package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemRelationService {

    private final ArchiveMapper archiveMapper;
    private final ArchiveItemRoutingService archiveItemService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemRelationService(
            ArchiveMapper archiveMapper,
            ArchiveItemRoutingService archiveItemService,
            AuthorizationPermissionService permissionService) {
        this.archiveMapper = archiveMapper;
        this.archiveItemService = archiveItemService;
        this.permissionService = permissionService;
    }

    public List<ArchiveItemRelationDto> listRelations(
            Long itemId, @Nullable Integer depth, Long userId) {
        return listRelationsInternal(itemId, depth, userId);
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
        archiveItemService.assertItemInDataScope(itemId, userId);
        archiveItemService.assertItemInDataScope(request.targetItemId(), userId);
        try {
            archiveMapper.insertItemRelation(itemId, request.targetItemId());
        } catch (DuplicateKeyException exception) {
            throw badRequest("关联档案已存在", "targetItemId", "关联档案已存在");
        }
        return listRelationsInternal(itemId, 1, userId).stream()
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
        archiveItemService.assertItemInDataScope(itemId, userId);
        Long targetItemId =
                archiveMapper.listItemRelations(itemId).stream()
                        .filter(row -> relationId.equals(number(row, "id").longValue()))
                        .map(row -> number(row, "targetItemId").longValue())
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在"));
        archiveItemService.assertItemInDataScope(targetItemId, userId);
        if (archiveMapper.deleteItemRelation(relationId, itemId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "关联档案不存在");
        }
    }

    private List<ArchiveItemRelationDto> listRelationsInternal(
            Long itemId, @Nullable Integer depth, Long userId) {
        requirePermission(userId, "archive:item:read");
        int normalizedDepth = depth == null ? 1 : depth;
        if (normalizedDepth < 1 || normalizedDepth > 2) {
            throw badRequest("关联档案展开层级必须在 1 到 2 之间", "depth", "关联档案展开层级必须在 1 到 2 之间");
        }
        archiveItemService.assertItemInDataScope(itemId, userId);
        return archiveMapper.listItemRelations(itemId).stream()
                .peek(
                        row ->
                                archiveItemService.assertItemInDataScope(
                                        number(row, "targetItemId").longValue(), userId))
                .map(this::toRelationDto)
                .toList();
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

    private @Nullable LocalDateTime dateTime(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        return row.containsKey(key)
                ? row.get(key)
                : row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

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
}
