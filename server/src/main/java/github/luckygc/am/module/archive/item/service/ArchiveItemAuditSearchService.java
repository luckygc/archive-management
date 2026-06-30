package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item._ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemAuditSearchService {

    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;

    private final ArchiveItemAuditDataRepository auditRepository;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveItemRoutingService archiveItemRoutingService;

    public ArchiveItemAuditSearchService(
            ArchiveItemAuditDataRepository auditRepository,
            AuthorizationPermissionService permissionService,
            ArchiveDataScopeService dataScopeService,
            ArchiveItemRoutingService archiveItemRoutingService) {
        this.auditRepository = auditRepository;
        this.permissionService = permissionService;
        this.dataScopeService = dataScopeService;
        this.archiveItemRoutingService = archiveItemRoutingService;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ArchiveItemAuditResponse> listAudits(
            @Nullable ListArchiveItemAuditsRequest query, Long userId) {
        requirePermission(userId, "archive:audit:read");
        ListArchiveItemAuditsRequest effectiveQuery =
                query == null ? ListArchiveItemAuditsRequest.empty() : query;
        int limit = pageLimit(effectiveQuery.limit());
        AuditSearchCriteria criteria = AuditSearchCriteria.from(effectiveQuery, limit);
        @Nullable CursorPageResponse<ArchiveItemAuditResponse> rangeResult =
                applyDataScope(criteria, userId);
        if (rangeResult != null) {
            return rangeResult;
        }
        CursoredPage<ArchiveItemAudit> page =
                auditRepository.find(auditRestriction(criteria), pageRequest(criteria));
        boolean filterByDataScope = requiresAuditItemScopeFilter(criteria, userId);
        List<ArchiveItemAudit> visibleAudits =
                filterByDataScope ? visibleAudits(page.content(), userId) : page.content();
        List<ArchiveItemAuditResponse> pageItems =
                visibleAudits.stream().map(this::toResponse).toList();
        return new CursorPageResponse<>(
                pageItems,
                page.numberOfElements() == 0
                        ? null
                        : encodeCursor("self", criteria, page.cursor(0)),
                page.hasPrevious() ? encodeCursor("prev", criteria, page.cursor(0)) : null,
                page.hasNext()
                        ? encodeCursor("next", criteria, page.cursor(page.numberOfElements() - 1))
                        : null,
                null,
                page.hasTotals()
                        ? (filterByDataScope ? (long) visibleAudits.size() : page.totalElements())
                        : null);
    }

    private @Nullable CursorPageResponse<ArchiveItemAuditResponse> applyDataScope(
            AuditSearchCriteria criteria, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (criteria.archiveItemId() != null) {
            archiveItemRoutingService.assertItemInDataScope(criteria.archiveItemId(), userId);
            return null;
        }
        if (dataScopeService.resolveUserDataScope(userId).allData()) {
            return null;
        }
        return null;
    }

    private boolean requiresAuditItemScopeFilter(AuditSearchCriteria criteria, Long userId) {
        return criteria.archiveItemId() == null
                && !dataScopeService.resolveUserDataScope(userId).allData();
    }

    private List<ArchiveItemAudit> visibleAudits(List<ArchiveItemAudit> audits, Long userId) {
        List<ArchiveItemAudit> visible = new ArrayList<>();
        for (ArchiveItemAudit audit : audits) {
            Long archiveItemId = audit.getArchiveItemId();
            if (archiveItemId == null) {
                continue;
            }
            try {
                archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
                visible.add(audit);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.FORBIDDEN
                        && exception.getStatusCode() != HttpStatus.NOT_FOUND) {
                    throw exception;
                }
            }
        }
        return visible;
    }

    private int pageLimit(@Nullable Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        if (limit <= 0) {
            throw new BadRequestException("分页参数不合法", "limit", "limit 必须大于 0");
        }
        if (limit > MAX_PAGE_LIMIT) {
            throw new BadRequestException("分页参数不合法", "limit", "limit 不能大于 1000");
        }
        return limit;
    }

    private void requirePermission(Long userId, String permissionCode) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private PageRequest pageRequest(AuditSearchCriteria criteria) {
        PageRequest pageRequest =
                criteria.requestTotal() && StringUtils.isBlank(criteria.cursor())
                        ? PageRequest.ofSize(criteria.limit()).withTotal()
                        : PageRequest.ofSize(criteria.limit()).withoutTotal();
        DecodedCursor cursor = decodeCursor(criteria.cursor(), criteria.fingerprint());
        if (cursor == null) {
            return pageRequest;
        }
        PageRequest.Cursor pageCursor =
                PageRequest.Cursor.forKey(cursor.values().toArray(Object[]::new));
        return "prev".equals(cursor.direction())
                ? pageRequest.beforeCursor(pageCursor)
                : pageRequest.afterCursor(pageCursor);
    }

    private Restriction<ArchiveItemAudit> auditRestriction(AuditSearchCriteria criteria) {
        List<Restriction<ArchiveItemAudit>> restrictions = new ArrayList<>();
        if (criteria.archiveItemId() != null) {
            restrictions.add(_ArchiveItemAudit.archiveItemId.equalTo(criteria.archiveItemId()));
        }
        if (criteria.fondsCode() != null) {
            restrictions.add(_ArchiveItemAudit.fondsCode.equalTo(criteria.fondsCode()));
        }
        if (criteria.categoryCode() != null) {
            restrictions.add(_ArchiveItemAudit.categoryCode.equalTo(criteria.categoryCode()));
        }
        if (criteria.operationType() != null) {
            restrictions.add(_ArchiveItemAudit.operationType.equalTo(criteria.operationType()));
        }
        if (criteria.operatedAfter() != null) {
            restrictions.add(
                    _ArchiveItemAudit.operatedAt.greaterThanEqual(criteria.operatedAfter()));
        }
        if (criteria.operatedBefore() != null) {
            restrictions.add(_ArchiveItemAudit.operatedAt.lessThan(criteria.operatedBefore()));
        }
        return restrictions.isEmpty() ? Restrict.unrestricted() : Restrict.all(restrictions);
    }

    private @Nullable String encodeCursor(
            String direction, AuditSearchCriteria criteria, PageRequest.Cursor cursor) {
        List<?> elements = cursor.elements();
        if (elements.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        values.add("v1");
        values.add(direction);
        values.add(criteria.fingerprint());
        for (Object element : elements) {
            values.add(element.toString());
        }
        return String.join("|", values);
    }

    private @Nullable DecodedCursor decodeCursor(@Nullable String cursor, String fingerprint) {
        if (StringUtils.isBlank(cursor)) {
            return null;
        }
        String[] parts = cursor.split("\\|", -1);
        if (parts.length < 5 || !"v1".equals(parts[0])) {
            throw new BadRequestException("分页 cursor 无效", "cursor", "cursor 格式无效");
        }
        if ((!"next".equals(parts[1]) && !"prev".equals(parts[1]))
                || !fingerprint.equals(parts[2])) {
            throw new BadRequestException("分页 cursor 无效", "cursor", "cursor 查询条件不匹配");
        }
        List<Object> values = new ArrayList<>();
        values.add(LocalDateTime.parse(parts[3]));
        values.add(Long.valueOf(parts[4]));
        return new DecodedCursor(parts[1], values);
    }

    private ArchiveItemAuditResponse toResponse(ArchiveItemAudit audit) {
        return new ArchiveItemAuditResponse(
                audit.getId(),
                audit.getSourceTableName(),
                audit.getSourceRecordId(),
                audit.getArchiveItemId(),
                audit.getFondsCode(),
                audit.getCategoryCode(),
                audit.getOperationType(),
                audit.getOperationReason(),
                audit.getOperatedBy(),
                audit.getOperatedAt());
    }

    public record ListArchiveItemAuditsRequest(
            @Nullable Long archiveItemId,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable String operationType,
            @Nullable LocalDateTime operatedAfter,
            @Nullable LocalDateTime operatedBefore,
            @Nullable Integer limit,
            @Nullable String cursor,
            boolean requestTotal) {

        private static ListArchiveItemAuditsRequest empty() {
            return new ListArchiveItemAuditsRequest(
                    null, null, null, null, null, null, null, null, false);
        }
    }

    private record AuditSearchCriteria(
            @Nullable Long archiveItemId,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable String operationType,
            @Nullable LocalDateTime operatedAfter,
            @Nullable LocalDateTime operatedBefore,
            int limit,
            @Nullable String cursor,
            boolean requestTotal,
            String fingerprint) {

        private static AuditSearchCriteria from(ListArchiveItemAuditsRequest query, int limit) {
            String fondsCode = StringUtils.trimToNull(query.fondsCode());
            String categoryCode = StringUtils.trimToNull(query.categoryCode());
            String operationType =
                    StringUtils.upperCase(StringUtils.trimToNull(query.operationType()));
            String fingerprint =
                    Integer.toHexString(
                            java.util.Objects.hash(
                                    query.archiveItemId(),
                                    fondsCode,
                                    categoryCode,
                                    operationType,
                                    query.operatedAfter(),
                                    query.operatedBefore(),
                                    limit,
                                    "operatedAt:desc",
                                    "id:desc"));
            return new AuditSearchCriteria(
                    query.archiveItemId(),
                    fondsCode,
                    categoryCode,
                    operationType,
                    query.operatedAfter(),
                    query.operatedBefore(),
                    limit,
                    query.cursor(),
                    query.requestTotal(),
                    fingerprint);
        }
    }

    private record DecodedCursor(String direction, List<Object> values) {}

    public record ArchiveItemAuditResponse(
            Long id,
            String sourceTableName,
            Long sourceItemId,
            @Nullable Long archiveItemId,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            String operationType,
            @Nullable String operationReason,
            Long operatedBy,
            LocalDateTime operatedAt) {}
}
