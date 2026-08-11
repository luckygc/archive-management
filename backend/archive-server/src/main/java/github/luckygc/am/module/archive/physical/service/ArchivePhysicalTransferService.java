package github.luckygc.am.module.archive.physical.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.physical.ArchivePhysicalCustodyStatus;
import github.luckygc.am.module.archive.physical.ArchivePhysicalObject;
import github.luckygc.am.module.archive.physical.ArchivePhysicalTransfer;
import github.luckygc.am.module.archive.physical.ArchivePhysicalTransferItem;
import github.luckygc.am.module.archive.physical.ArchivePhysicalTransferStatus;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalTransferDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalTransferItemDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;

@Service
public class ArchivePhysicalTransferService {

    private static final int MAX_BATCH_SIZE = 500;

    private final ArchivePhysicalTransferDataRepository transferRepository;
    private final ArchivePhysicalTransferItemDataRepository itemRepository;
    private final ArchivePhysicalObjectDataRepository physicalObjectRepository;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveVolumeService archiveVolumeService;
    private final OrganizationDepartmentService departmentService;
    private final AuthorizationPermissionService permissionService;
    private final Clock clock;

    public ArchivePhysicalTransferService(
            ArchivePhysicalTransferDataRepository transferRepository,
            ArchivePhysicalTransferItemDataRepository itemRepository,
            ArchivePhysicalObjectDataRepository physicalObjectRepository,
            ArchiveItemReadService archiveItemReadService,
            ArchiveVolumeService archiveVolumeService,
            OrganizationDepartmentService departmentService,
            AuthorizationPermissionService permissionService,
            Clock clock) {
        this.transferRepository = transferRepository;
        this.itemRepository = itemRepository;
        this.physicalObjectRepository = physicalObjectRepository;
        this.archiveItemReadService = archiveItemReadService;
        this.archiveVolumeService = archiveVolumeService;
        this.departmentService = departmentService;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Transactional
    public ArchivePhysicalTransferResponse create(
            CreateArchivePhysicalTransferRequest request, Long userId) {
        userId = requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        String transferNo = requiredText(request.transferNo(), 80, "移交编号不能为空", "transferNo");
        if (transferRepository.findByTransferNo(transferNo).isPresent()) {
            throw new BadRequestException("移交编号已存在", "transferNo", "移交编号已存在");
        }
        if (request.sourceDepartmentId() == null) {
            throw new BadRequestException("来源部门不能为空", "sourceDepartmentId", "来源部门不能为空");
        }
        departmentService.requireEnabledDepartment(request.sourceDepartmentId());
        List<Long> physicalObjectIds = validatePhysicalObjectIds(request.physicalObjectIds());
        List<ArchivePhysicalObject> physicalObjects =
                physicalObjectIds.stream().map(this::physicalObject).toList();
        for (ArchivePhysicalObject physicalObject : physicalObjects) {
            assertOwnerInDataScope(physicalObject, userId);
            if (physicalObject.getCustodyStatus()
                    != ArchivePhysicalCustodyStatus.DEPARTMENT_CUSTODY) {
                throw new BadRequestException("实物已经接收或正在其他移交批次中");
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ArchivePhysicalTransfer transfer = new ArchivePhysicalTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setSourceDepartmentId(request.sourceDepartmentId());
        transfer.setStatus(ArchivePhysicalTransferStatus.PENDING_RECEIPT);
        transfer.setRemark(optionalText(request.remark(), 1000, "remark", "备注不能超过 1000 个字符"));
        transfer.setSubmittedBy(userId);
        transfer.setSubmittedAt(now);
        try {
            transfer = transferRepository.insert(transfer);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("移交编号已存在", "transferNo", "移交编号已存在");
        }

        List<ArchivePhysicalTransferItem> items = new java.util.ArrayList<>(physicalObjects.size());
        for (ArchivePhysicalObject physicalObject : physicalObjects) {
            ArchivePhysicalTransferItem item = snapshot(transfer.getId(), physicalObject);
            try {
                item = itemRepository.insert(item);
            } catch (DataIntegrityViolationException exception) {
                throw new BadRequestException("实物正在其他移交批次中");
            }
            physicalObject.setCustodyStatus(ArchivePhysicalCustodyStatus.PENDING_RECEIPT);
            physicalObjectRepository.update(physicalObject);
            items.add(item);
        }
        return toResponse(transfer, items);
    }

    @Transactional(readOnly = true)
    public ArchivePhysicalTransferResponse get(Long id, Long userId) {
        userId = requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);
        ArchivePhysicalTransfer transfer = transfer(id);
        List<ArchivePhysicalTransferItem> items = itemRepository.findByTransferId(id);
        assertItemsInDataScope(items, userId);
        return toResponse(transfer, items);
    }

    @Transactional
    public ArchivePhysicalTransferResponse accept(
            Long id, AcceptArchivePhysicalTransferRequest request, Long userId) {
        userId = requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
        ArchivePhysicalTransfer transfer = transfer(id);
        List<ArchivePhysicalTransferItem> items = pendingItems(transfer, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        transfer.setStatus(ArchivePhysicalTransferStatus.ACCEPTED);
        transfer.setReceivedBy(userId);
        transfer.setReceivedAt(now);
        transfer.setReceiptNote(
                optionalText(
                        request == null ? null : request.receiptNote(),
                        1000,
                        "receiptNote",
                        "接收说明不能超过 1000 个字符"));
        transferRepository.update(transfer);
        completeItems(items, ArchivePhysicalCustodyStatus.ARCHIVE_ROOM_CUSTODY);
        return toResponse(transfer, items);
    }

    @Transactional
    public ArchivePhysicalTransferResponse reject(
            Long id, RejectArchivePhysicalTransferRequest request, Long userId) {
        userId = requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
        ArchivePhysicalTransfer transfer = transfer(id);
        List<ArchivePhysicalTransferItem> items = pendingItems(transfer, userId);
        String reason =
                requiredText(request == null ? null : request.reason(), 1000, "退回原因不能为空", "reason");
        LocalDateTime now = LocalDateTime.now(clock);
        transfer.setStatus(ArchivePhysicalTransferStatus.REJECTED);
        transfer.setRejectedBy(userId);
        transfer.setRejectedAt(now);
        transfer.setRejectionReason(reason);
        transferRepository.update(transfer);
        completeItems(items, ArchivePhysicalCustodyStatus.DEPARTMENT_CUSTODY);
        return toResponse(transfer, items);
    }

    private List<ArchivePhysicalTransferItem> pendingItems(
            ArchivePhysicalTransfer transfer, Long userId) {
        if (transfer.getStatus() != ArchivePhysicalTransferStatus.PENDING_RECEIPT) {
            throw new BadRequestException("实物移交批次已经处理");
        }
        List<ArchivePhysicalTransferItem> items = itemRepository.findByTransferId(transfer.getId());
        if (items.isEmpty()) {
            throw new BadRequestException("实物移交批次没有清单");
        }
        assertItemsInDataScope(items, userId);
        return items;
    }

    private void completeItems(
            List<ArchivePhysicalTransferItem> items, ArchivePhysicalCustodyStatus targetStatus) {
        for (ArchivePhysicalTransferItem item : items) {
            if (!item.isActiveFlag()) {
                throw new BadRequestException("实物移交清单已经处理");
            }
            ArchivePhysicalObject physicalObject = physicalObject(item.getPhysicalObjectId());
            if (physicalObject.getCustodyStatus() != ArchivePhysicalCustodyStatus.PENDING_RECEIPT) {
                throw new BadRequestException("实物保管状态与待接收批次不一致");
            }
            physicalObject.setCustodyStatus(targetStatus);
            physicalObjectRepository.update(physicalObject);
            item.setActiveFlag(false);
            itemRepository.update(item);
        }
    }

    private ArchivePhysicalTransferItem snapshot(
            Long transferId, ArchivePhysicalObject physicalObject) {
        ArchivePhysicalTransferItem item = new ArchivePhysicalTransferItem();
        item.setTransferId(transferId);
        item.setPhysicalObjectId(physicalObject.getId());
        item.setArchiveItemId(physicalObject.getArchiveItemId());
        item.setArchiveVolumeId(physicalObject.getArchiveVolumeId());
        item.setBarcodeSnapshot(physicalObject.getBarcode());
        item.setCarrierTypeSnapshot(physicalObject.getCarrierType());
        item.setQuantitySnapshot(physicalObject.getQuantity());
        item.setQuantityUnitSnapshot(physicalObject.getQuantityUnit());
        item.setConditionNoteSnapshot(physicalObject.getConditionNote());
        item.setActiveFlag(true);
        return item;
    }

    private List<Long> validatePhysicalObjectIds(@Nullable List<@Nullable Long> physicalObjectIds) {
        if (physicalObjectIds == null
                || physicalObjectIds.isEmpty()
                || physicalObjectIds.size() > MAX_BATCH_SIZE) {
            throw new BadRequestException("实物对象数量必须在 1 到 " + MAX_BATCH_SIZE + " 之间");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : physicalObjectIds) {
            if (id == null || id <= 0) {
                throw new BadRequestException("实物对象 ID 不合法");
            }
            if (!ids.add(id)) {
                throw new BadRequestException("实物对象不能重复");
            }
        }
        return List.copyOf(ids);
    }

    private ArchivePhysicalTransfer transfer(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("实物移交批次 ID 不合法");
        }
        return transferRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "实物移交批次不存在"));
    }

    private ArchivePhysicalObject physicalObject(Long id) {
        return physicalObjectRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "实物对象不存在"));
    }

    private void assertItemsInDataScope(List<ArchivePhysicalTransferItem> items, Long userId) {
        for (ArchivePhysicalTransferItem item : items) {
            assertOwnerInDataScope(item.getArchiveItemId(), item.getArchiveVolumeId(), userId);
        }
    }

    private void assertOwnerInDataScope(ArchivePhysicalObject physicalObject, Long userId) {
        assertOwnerInDataScope(
                physicalObject.getArchiveItemId(), physicalObject.getArchiveVolumeId(), userId);
    }

    private void assertOwnerInDataScope(
            @Nullable Long archiveItemId, @Nullable Long archiveVolumeId, Long userId) {
        if (archiveItemId != null) {
            archiveItemReadService.assertItemInDataScope(archiveItemId, userId);
        } else {
            archiveVolumeService.assertVolumeInDataScope(
                    Objects.requireNonNull(archiveVolumeId), userId);
        }
    }

    private Long requirePermission(Long userId, AuthorizationPermissionCode permission) {
        Long resolvedUserId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(resolvedUserId, permission.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
        return resolvedUserId;
    }

    private String requiredText(
            @Nullable String value, int maxLength, String emptyMessage, String field) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(emptyMessage, field, emptyMessage);
        }
        if (normalized.length() > maxLength) {
            throw new BadRequestException(
                    field + " 长度不能超过 " + maxLength, field, field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private @Nullable String optionalText(
            @Nullable String value, int maxLength, String field, String message) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BadRequestException(message, field, message);
        }
        return normalized;
    }

    private ArchivePhysicalTransferResponse toResponse(
            ArchivePhysicalTransfer transfer, List<ArchivePhysicalTransferItem> items) {
        return new ArchivePhysicalTransferResponse(
                transfer.getId(),
                transfer.getTransferNo(),
                transfer.getSourceDepartmentId(),
                transfer.getStatus(),
                transfer.getRemark(),
                transfer.getSubmittedBy(),
                transfer.getSubmittedAt(),
                transfer.getReceivedBy(),
                transfer.getReceivedAt(),
                transfer.getReceiptNote(),
                transfer.getRejectedBy(),
                transfer.getRejectedAt(),
                transfer.getRejectionReason(),
                items.stream().map(this::toItemResponse).toList(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt());
    }

    private ArchivePhysicalTransferItemResponse toItemResponse(ArchivePhysicalTransferItem item) {
        return new ArchivePhysicalTransferItemResponse(
                item.getId(),
                item.getPhysicalObjectId(),
                item.getArchiveItemId(),
                item.getArchiveVolumeId(),
                item.getBarcodeSnapshot(),
                item.getCarrierTypeSnapshot(),
                item.getQuantitySnapshot(),
                item.getQuantityUnitSnapshot(),
                item.getConditionNoteSnapshot());
    }

    public record CreateArchivePhysicalTransferRequest(
            @Nullable String transferNo,
            @Nullable Long sourceDepartmentId,
            @Nullable List<@Nullable Long> physicalObjectIds,
            @Nullable String remark) {}

    public record AcceptArchivePhysicalTransferRequest(@Nullable String receiptNote) {}

    public record RejectArchivePhysicalTransferRequest(@Nullable String reason) {}

    public record ArchivePhysicalTransferResponse(
            Long id,
            String transferNo,
            Long sourceDepartmentId,
            ArchivePhysicalTransferStatus status,
            @Nullable String remark,
            Long submittedBy,
            LocalDateTime submittedAt,
            @Nullable Long receivedBy,
            @Nullable LocalDateTime receivedAt,
            @Nullable String receiptNote,
            @Nullable Long rejectedBy,
            @Nullable LocalDateTime rejectedAt,
            @Nullable String rejectionReason,
            List<ArchivePhysicalTransferItemResponse> items,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchivePhysicalTransferItemResponse(
            Long id,
            Long physicalObjectId,
            @Nullable Long archiveItemId,
            @Nullable Long archiveVolumeId,
            @Nullable String barcode,
            @Nullable String carrierType,
            @Nullable BigDecimal quantity,
            @Nullable String quantityUnit,
            @Nullable String conditionNote) {}
}
