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
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item._ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemAuditSearchService {

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
            @Nullable ListArchiveItemAuditsRequest query, PageRequest pageRequest, Long userId) {
        userId = requireAuditReadPermission(userId);
        ListArchiveItemAuditsRequest effectiveQuery =
                query == null ? ListArchiveItemAuditsRequest.empty() : query;
        AuditSearchCriteria criteria = AuditSearchCriteria.from(effectiveQuery);
        CursoredPage<ArchiveItemAudit> page =
                auditRepository.find(auditRestriction(criteria), pageRequest);
        return CursorPageResponse.from(page, pageRequest, this::toResponse);
    }

    private Long requireAuditReadPermission(Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.isSuperAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
        return userId;
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
            @Nullable LocalDateTime operatedBefore) {

        private static ListArchiveItemAuditsRequest empty() {
            return new ListArchiveItemAuditsRequest(null, null, null, null, null, null);
        }
    }

    private record AuditSearchCriteria(
            @Nullable Long archiveItemId,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable String operationType,
            @Nullable LocalDateTime operatedAfter,
            @Nullable LocalDateTime operatedBefore) {

        private static AuditSearchCriteria from(ListArchiveItemAuditsRequest query) {
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
                    query.operatedBefore());
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
