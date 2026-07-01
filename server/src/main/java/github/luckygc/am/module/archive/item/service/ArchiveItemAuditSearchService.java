package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.data.page.CursoredPage;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.exception.BadRequestException;
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

    public ArchiveItemAuditSearchService(
            ArchiveItemAuditDataRepository auditRepository,
            AuthorizationPermissionService permissionService) {
        this.auditRepository = auditRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ArchiveItemAuditResponse> listAudits(
            @Nullable ListArchiveItemAuditsRequest query, Long userId) {
        requireSuperAdmin(userId);
        ListArchiveItemAuditsRequest effectiveQuery =
                query == null ? ListArchiveItemAuditsRequest.empty() : query;
        int limit = pageLimit(effectiveQuery.limit());
        AuditSearchCriteria criteria = AuditSearchCriteria.from(effectiveQuery, limit);
        CursoredPage<ArchiveItemAudit> page =
                auditRepository.find(
                        auditRestriction(criteria),
                        CursorPageTokenCodec.pageRequest(
                                criteria.limit(), criteria.cursor(), criteria.requestTotal()));
        return CursorPageResponse.from(page, this::toResponse);
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

    private void requireSuperAdmin(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!permissionService.isSuperAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
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
            boolean requestTotal) {

        private static AuditSearchCriteria from(ListArchiveItemAuditsRequest query, int limit) {
            String fondsCode = StringUtils.trimToNull(query.fondsCode());
            String categoryCode = StringUtils.trimToNull(query.categoryCode());
            String operationType =
                    StringUtils.upperCase(StringUtils.trimToNull(query.operationType()));
            return new AuditSearchCriteria(
                    query.archiveItemId(),
                    fondsCode,
                    categoryCode,
                    operationType,
                    query.operatedAfter(),
                    query.operatedBefore(),
                    limit,
                    query.cursor(),
                    query.requestTotal());
        }
    }

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
