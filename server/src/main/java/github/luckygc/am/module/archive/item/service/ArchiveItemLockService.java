package github.luckygc.am.module.archive.item.service;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemLockService {

    private static final String AUDIT_OPERATION_LOCK = "LOCK";
    private static final String AUDIT_OPERATION_UNLOCK = "UNLOCK";

    private final ArchiveMapper archiveMapper;
    private final ArchiveItemRoutingService archiveItemService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemAuditDataRepository auditRepository;

    public ArchiveItemLockService(
            ArchiveMapper archiveMapper,
            ArchiveItemRoutingService archiveItemService,
            AuthorizationPermissionService permissionService,
            ArchiveItemAuditDataRepository auditRepository) {
        this.archiveMapper = archiveMapper;
        this.archiveItemService = archiveItemService;
        this.permissionService = permissionService;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public ArchiveItemDto lockItem(Long id, Long userId, @Nullable LockItemRequest request) {
        requireLockPermission(userId);
        requireId(id);
        ArchiveItemDto before = archiveItemService.getItem(id);
        archiveItemService.assertItemInDataScope(before.id(), userId);
        String reason = request == null ? null : StringUtils.trimToNull(request.reason());
        if (archiveMapper.lockArchiveItem(id, reason, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
        ArchiveItemDto after = archiveItemService.getItem(id);
        insertItemAudit(AUDIT_OPERATION_LOCK, after, reason, userId);
        return after;
    }

    @Transactional
    public ArchiveItemDto unlockItem(Long id, Long userId) {
        requireLockPermission(userId);
        requireId(id);
        ArchiveItemDto before = archiveItemService.getItem(id);
        archiveItemService.assertItemInDataScope(before.id(), userId);
        if (archiveMapper.unlockArchiveItem(id, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
        ArchiveItemDto after = archiveItemService.getItem(id);
        insertItemAudit(AUDIT_OPERATION_UNLOCK, after, null, userId);
        return after;
    }

    private void requireLockPermission(Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, "archive:item:lock")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("档案条目 ID 不合法");
        }
    }

    private void insertItemAudit(
            String operationType,
            ArchiveItemDto record,
            @Nullable String operationReason,
            Long operatedBy) {
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

    public record LockItemRequest(@Nullable String reason) {}
}
