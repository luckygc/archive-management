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
import github.luckygc.am.module.archive.physical.ArchivePhysicalLocationHistory;
import github.luckygc.am.module.archive.physical.ArchivePhysicalObject;
import github.luckygc.am.module.archive.physical.ArchiveStorageLocation;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalLocationHistoryDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchivePhysicalObjectService {

    private static final int MAX_BATCH_SIZE = 500;

    private final ArchivePhysicalObjectDataRepository objectRepository;
    private final ArchivePhysicalLocationHistoryDataRepository historyRepository;
    private final ArchiveStorageLocationService locationService;
    private final ArchiveItemReadService itemReadService;
    private final ArchiveVolumeService volumeService;
    private final AuthorizationPermissionService permissionService;
    private final Clock clock;

    public ArchivePhysicalObjectService(
            ArchivePhysicalObjectDataRepository objectRepository,
            ArchivePhysicalLocationHistoryDataRepository historyRepository,
            ArchiveStorageLocationService locationService,
            ArchiveItemReadService itemReadService,
            ArchiveVolumeService volumeService,
            AuthorizationPermissionService permissionService,
            Clock clock) {
        this.objectRepository = objectRepository;
        this.historyRepository = historyRepository;
        this.locationService = locationService;
        this.itemReadService = itemReadService;
        this.volumeService = volumeService;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Transactional
    public ArchivePhysicalObjectResponse create(
            CreateArchivePhysicalObjectRequest request, Long userId) {
        requireUpdatePermission(userId);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        validateOwner(request.archiveItemId(), request.archiveVolumeId());
        assertOwnerInDataScope(request.archiveItemId(), request.archiveVolumeId(), userId);
        ArchivePhysicalObject entity = new ArchivePhysicalObject();
        entity.setArchiveItemId(request.archiveItemId());
        entity.setArchiveVolumeId(request.archiveVolumeId());
        applyCommonFields(
                entity,
                request.barcode(),
                request.carrierType(),
                request.quantity(),
                request.quantityUnit(),
                request.conditionNote(),
                request.remark());
        try {
            return toResponse(objectRepository.insert(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("该档案已经存在实物信息");
        }
    }

    public ArchivePhysicalObjectResponse get(Long id, Long userId) {
        requireReadPermission(userId);
        ArchivePhysicalObject entity = object(id);
        assertOwnerInDataScope(entity.getArchiveItemId(), entity.getArchiveVolumeId(), userId);
        return toResponse(entity);
    }

    public ArchivePhysicalObjectResponse getByArchiveItem(Long archiveItemId, Long userId) {
        return getByOwner(archiveItemId, null, userId);
    }

    public ArchivePhysicalObjectResponse getByArchiveVolume(Long archiveVolumeId, Long userId) {
        return getByOwner(null, archiveVolumeId, userId);
    }

    private ArchivePhysicalObjectResponse getByOwner(
            @Nullable Long archiveItemId, @Nullable Long archiveVolumeId, Long userId) {
        requireReadPermission(userId);
        validateOwner(archiveItemId, archiveVolumeId);
        assertOwnerInDataScope(archiveItemId, archiveVolumeId, userId);
        return (archiveItemId != null
                        ? objectRepository.findByArchiveItemId(archiveItemId)
                        : objectRepository.findByArchiveVolumeId(archiveVolumeId))
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "实物对象不存在"));
    }

    @Transactional
    public ArchivePhysicalObjectResponse update(
            Long id, UpdateArchivePhysicalObjectRequest request, Long userId) {
        requireUpdatePermission(userId);
        ArchivePhysicalObject entity = object(id);
        assertOwnerInDataScope(entity.getArchiveItemId(), entity.getArchiveVolumeId(), userId);
        assertNotPendingReceipt(entity);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        applyCommonFields(
                entity,
                request.barcode(),
                request.carrierType(),
                request.quantity(),
                request.quantityUnit(),
                request.conditionNote(),
                request.remark());
        return toResponse(objectRepository.update(entity));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        requireUpdatePermission(userId);
        ArchivePhysicalObject entity = object(id);
        assertOwnerInDataScope(entity.getArchiveItemId(), entity.getArchiveVolumeId(), userId);
        assertNotPendingReceipt(entity);
        objectRepository.delete(entity);
    }

    @Transactional
    public BatchAssignArchiveLocationResponse batchAssignLocation(
            BatchAssignArchiveLocationRequest request, Long userId) {
        requireUpdatePermission(userId);
        if (request == null || request.physicalObjectIds() == null) {
            throw new BadRequestException("实物对象不能为空");
        }
        Set<Long> ids = new LinkedHashSet<>(request.physicalObjectIds());
        if (ids.isEmpty() || ids.size() > MAX_BATCH_SIZE || ids.contains(null)) {
            throw new BadRequestException("实物对象数量必须在 1 到 " + MAX_BATCH_SIZE + " 之间");
        }
        if (request.locationId() == null) {
            throw new BadRequestException("目标存放位置不能为空");
        }
        ArchiveStorageLocation target = locationService.getEnabledLocation(request.locationId());
        List<ArchivePhysicalObject> objects = ids.stream().map(this::object).toList();
        for (ArchivePhysicalObject object : objects) {
            assertOwnerInDataScope(object.getArchiveItemId(), object.getArchiveVolumeId(), userId);
            if (object.getCustodyStatus() != ArchivePhysicalCustodyStatus.ARCHIVE_ROOM_CUSTODY) {
                throw new BadRequestException("只有档案室保管的实物才能关联存放位置");
            }
        }
        int changed = 0;
        for (ArchivePhysicalObject object : objects) {
            if (Objects.equals(object.getCurrentLocationId(), target.getId())) {
                continue;
            }
            Long fromLocationId = object.getCurrentLocationId();
            object.setCurrentLocationId(target.getId());
            objectRepository.update(object);
            insertLocationHistory(object.getId(), fromLocationId, target.getId(), request, userId);
            changed++;
        }
        return new BatchAssignArchiveLocationResponse(ids.size(), changed, target.getId());
    }

    public List<ArchivePhysicalLocationHistoryResponse> listLocationHistory(Long id, Long userId) {
        requireReadPermission(userId);
        ArchivePhysicalObject object = object(id);
        assertOwnerInDataScope(object.getArchiveItemId(), object.getArchiveVolumeId(), userId);
        return historyRepository.list(id).stream().map(this::toHistoryResponse).toList();
    }

    private void insertLocationHistory(
            Long physicalObjectId,
            @Nullable Long fromLocationId,
            Long toLocationId,
            BatchAssignArchiveLocationRequest request,
            Long userId) {
        ArchivePhysicalLocationHistory history = new ArchivePhysicalLocationHistory();
        history.setPhysicalObjectId(physicalObjectId);
        history.setFromLocationId(fromLocationId);
        history.setToLocationId(toLocationId);
        history.setBusinessType(StringUtils.trimToNull(request.businessType()));
        history.setBusinessId(request.businessId());
        history.setReason(StringUtils.trimToNull(request.reason()));
        history.setOperatedBy(userId);
        history.setOperatedAt(LocalDateTime.now(clock));
        historyRepository.insert(history);
    }

    private void applyCommonFields(
            ArchivePhysicalObject entity,
            @Nullable String barcode,
            @Nullable String carrierType,
            @Nullable BigDecimal quantity,
            @Nullable String quantityUnit,
            @Nullable String conditionNote,
            @Nullable String remark) {
        if (quantity != null && quantity.signum() <= 0) {
            throw new BadRequestException("实物数量必须大于 0");
        }
        entity.setBarcode(StringUtils.trimToNull(barcode));
        entity.setCarrierType(StringUtils.trimToNull(carrierType));
        entity.setQuantity(quantity);
        entity.setQuantityUnit(StringUtils.trimToNull(quantityUnit));
        entity.setConditionNote(StringUtils.trimToNull(conditionNote));
        entity.setRemark(StringUtils.trimToNull(remark));
    }

    private void validateOwner(@Nullable Long archiveItemId, @Nullable Long archiveVolumeId) {
        if ((archiveItemId == null) == (archiveVolumeId == null)) {
            throw new BadRequestException("档案条目和案卷必须且只能选择一个");
        }
        if (archiveItemId != null && archiveItemId <= 0
                || archiveVolumeId != null && archiveVolumeId <= 0) {
            throw new BadRequestException("档案对象 ID 不合法");
        }
    }

    private void assertOwnerInDataScope(
            @Nullable Long archiveItemId, @Nullable Long archiveVolumeId, Long userId) {
        if (archiveItemId != null) {
            itemReadService.assertItemInDataScope(archiveItemId, userId);
        } else {
            volumeService.assertVolumeInDataScope(Objects.requireNonNull(archiveVolumeId), userId);
        }
    }

    private ArchivePhysicalObject object(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("实物对象 ID 不合法");
        }
        return objectRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "实物对象不存在"));
    }

    private void assertNotPendingReceipt(ArchivePhysicalObject object) {
        if (object.getCustodyStatus() == ArchivePhysicalCustodyStatus.PENDING_RECEIPT) {
            throw new BadRequestException("待接收实物不能修改或删除");
        }
    }

    private void requireReadPermission(Long userId) {
        requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);
    }

    private void requireUpdatePermission(Long userId) {
        requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
    }

    private void requirePermission(Long userId, AuthorizationPermissionCode permission) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permission.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private ArchivePhysicalObjectResponse toResponse(ArchivePhysicalObject entity) {
        return new ArchivePhysicalObjectResponse(
                entity.getId(),
                entity.getArchiveItemId(),
                entity.getArchiveVolumeId(),
                entity.getBarcode(),
                entity.getCarrierType(),
                entity.getQuantity(),
                entity.getQuantityUnit(),
                entity.getConditionNote(),
                entity.getCustodyStatus(),
                entity.getCurrentLocationId(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ArchivePhysicalLocationHistoryResponse toHistoryResponse(
            ArchivePhysicalLocationHistory entity) {
        return new ArchivePhysicalLocationHistoryResponse(
                entity.getId(),
                entity.getPhysicalObjectId(),
                entity.getFromLocationId(),
                entity.getToLocationId(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getReason(),
                entity.getOperatedBy(),
                entity.getOperatedAt());
    }

    public record CreateArchivePhysicalObjectRequest(
            @Nullable Long archiveItemId,
            @Nullable Long archiveVolumeId,
            @Nullable String barcode,
            @Nullable String carrierType,
            @Nullable BigDecimal quantity,
            @Nullable String quantityUnit,
            @Nullable String conditionNote,
            @Nullable String remark) {}

    public record UpdateArchivePhysicalObjectRequest(
            @Nullable String barcode,
            @Nullable String carrierType,
            @Nullable BigDecimal quantity,
            @Nullable String quantityUnit,
            @Nullable String conditionNote,
            @Nullable String remark) {}

    public record ArchivePhysicalObjectResponse(
            Long id,
            @Nullable Long archiveItemId,
            @Nullable Long archiveVolumeId,
            @Nullable String barcode,
            @Nullable String carrierType,
            @Nullable BigDecimal quantity,
            @Nullable String quantityUnit,
            @Nullable String conditionNote,
            ArchivePhysicalCustodyStatus custodyStatus,
            @Nullable Long currentLocationId,
            @Nullable String remark,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {}

    public record BatchAssignArchiveLocationRequest(
            @Nullable List<@Nullable Long> physicalObjectIds,
            @Nullable Long locationId,
            @Nullable String businessType,
            @Nullable Long businessId,
            @Nullable String reason) {}

    public record BatchAssignArchiveLocationResponse(
            int requestedCount, int changedCount, Long locationId) {}

    public record ArchivePhysicalLocationHistoryResponse(
            Long id,
            Long physicalObjectId,
            @Nullable Long fromLocationId,
            Long toLocationId,
            @Nullable String businessType,
            @Nullable Long businessId,
            @Nullable String reason,
            @Nullable Long operatedBy,
            LocalDateTime operatedAt) {}
}
