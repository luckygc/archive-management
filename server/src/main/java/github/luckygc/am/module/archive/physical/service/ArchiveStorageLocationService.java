package github.luckygc.am.module.archive.physical.service;

import java.util.HashSet;
import java.util.List;
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
import github.luckygc.am.module.archive.physical.ArchiveStorageLocation;
import github.luckygc.am.module.archive.physical.ArchiveWarehouse;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchiveStorageLocationDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchiveWarehouseDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveStorageLocationService {

    private final ArchiveWarehouseDataRepository warehouseRepository;
    private final ArchiveStorageLocationDataRepository locationRepository;
    private final ArchivePhysicalObjectDataRepository physicalObjectRepository;
    private final AuthorizationPermissionService permissionService;

    public ArchiveStorageLocationService(
            ArchiveWarehouseDataRepository warehouseRepository,
            ArchiveStorageLocationDataRepository locationRepository,
            ArchivePhysicalObjectDataRepository physicalObjectRepository,
            AuthorizationPermissionService permissionService) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.physicalObjectRepository = physicalObjectRepository;
        this.permissionService = permissionService;
    }

    public List<ArchiveWarehouseResponse> listWarehouses(@Nullable Boolean enabled) {
        List<ArchiveWarehouse> rows =
                enabled == null ? warehouseRepository.list() : warehouseRepository.list(enabled);
        return rows.stream().map(this::toWarehouseResponse).toList();
    }

    @Transactional
    public ArchiveWarehouseResponse createWarehouse(
            CreateArchiveWarehouseRequest request, Long userId) {
        requireManagePermission(userId);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        ArchiveWarehouse entity = new ArchiveWarehouse();
        entity.setWarehouseCode(required(request.warehouseCode(), "库房编码不能为空"));
        entity.setWarehouseName(required(request.warehouseName(), "库房名称不能为空"));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        try {
            return toWarehouseResponse(warehouseRepository.insert(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("库房编码已存在", "warehouseCode", "库房编码已存在");
        }
    }

    @Transactional
    public ArchiveWarehouseResponse updateWarehouse(
            Long id, UpdateArchiveWarehouseRequest request, Long userId) {
        requireManagePermission(userId);
        ArchiveWarehouse entity = warehouse(id);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (request.warehouseCode() != null) {
            entity.setWarehouseCode(required(request.warehouseCode(), "库房编码不能为空"));
        }
        if (request.warehouseName() != null) {
            entity.setWarehouseName(required(request.warehouseName(), "库房名称不能为空"));
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        try {
            return toWarehouseResponse(warehouseRepository.update(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("库房编码已存在", "warehouseCode", "库房编码已存在");
        }
    }

    @Transactional
    public void deleteWarehouse(Long id, Long userId) {
        requireManagePermission(userId);
        ArchiveWarehouse entity = warehouse(id);
        if (!locationRepository.list(id).isEmpty()) {
            throw new BadRequestException("库房仍有存放位置，不能删除");
        }
        warehouseRepository.delete(entity);
    }

    public List<ArchiveStorageLocationResponse> listLocations(@Nullable Long warehouseId) {
        List<ArchiveStorageLocation> rows =
                warehouseId == null
                        ? locationRepository.list()
                        : locationRepository.list(warehouseId);
        return rows.stream().map(this::toLocationResponse).toList();
    }

    @Transactional
    public ArchiveStorageLocationResponse createLocation(
            CreateArchiveStorageLocationRequest request, Long userId) {
        requireManagePermission(userId);
        if (request == null || request.warehouseId() == null) {
            throw new BadRequestException("真实库房不能为空");
        }
        ArchiveWarehouse warehouse = enabledWarehouse(request.warehouseId());
        ArchiveStorageLocation entity = new ArchiveStorageLocation();
        entity.setWarehouseId(warehouse.getId());
        entity.setParentId(request.parentId());
        entity.setLocationCode(required(request.locationCode(), "位置编码不能为空"));
        entity.setLocationName(required(request.locationName(), "位置名称不能为空"));
        entity.setLocationType(required(request.locationType(), "位置类型不能为空"));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        validateParent(entity.getWarehouseId(), entity.getParentId(), null);
        return insertLocation(entity);
    }

    @Transactional
    public ArchiveStorageLocationResponse updateLocation(
            Long id, UpdateArchiveStorageLocationRequest request, Long userId) {
        requireManagePermission(userId);
        ArchiveStorageLocation entity = location(id);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        Long warehouseId =
                request.warehouseId() == null ? entity.getWarehouseId() : request.warehouseId();
        enabledWarehouse(warehouseId);
        Long parentId = request.parentIdPresent() ? request.parentId() : entity.getParentId();
        if (!warehouseId.equals(entity.getWarehouseId())
                && (!locationRepository.findByParentId(id).isEmpty()
                        || !physicalObjectRepository.findByCurrentLocationId(id).isEmpty())) {
            throw new BadRequestException("存在子位置或实物时不能变更所属库房");
        }
        validateParent(warehouseId, parentId, id);
        entity.setWarehouseId(warehouseId);
        entity.setParentId(parentId);
        if (request.locationCode() != null) {
            entity.setLocationCode(required(request.locationCode(), "位置编码不能为空"));
        }
        if (request.locationName() != null) {
            entity.setLocationName(required(request.locationName(), "位置名称不能为空"));
        }
        if (request.locationType() != null) {
            entity.setLocationType(required(request.locationType(), "位置类型不能为空"));
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        try {
            return toLocationResponse(locationRepository.update(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("同一库房内位置编码已存在");
        }
    }

    @Transactional
    public void deleteLocation(Long id, Long userId) {
        requireManagePermission(userId);
        ArchiveStorageLocation entity = location(id);
        if (!locationRepository.findByParentId(id).isEmpty()) {
            throw new BadRequestException("位置仍有子位置，不能删除");
        }
        if (!physicalObjectRepository.findByCurrentLocationId(id).isEmpty()) {
            throw new BadRequestException("位置仍有关联实物，不能删除");
        }
        locationRepository.delete(entity);
    }

    public ArchiveStorageLocation getEnabledLocation(Long id) {
        ArchiveStorageLocation entity = location(id);
        if (!entity.isEnabled()) {
            throw new BadRequestException("目标存放位置已禁用");
        }
        ArchiveWarehouse warehouse = enabledWarehouse(entity.getWarehouseId());
        if (!warehouse.isEnabled()) {
            throw new BadRequestException("目标存放位置所属库房已禁用");
        }
        return entity;
    }

    private ArchiveStorageLocationResponse insertLocation(ArchiveStorageLocation entity) {
        try {
            return toLocationResponse(locationRepository.insert(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("同一库房内位置编码已存在");
        }
    }

    private void validateParent(
            Long warehouseId, @Nullable Long parentId, @Nullable Long currentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BadRequestException("位置不能引用自身作为父位置");
        }
        ArchiveStorageLocation parent = location(parentId);
        if (!warehouseId.equals(parent.getWarehouseId())) {
            throw new BadRequestException("父位置必须属于同一真实库房");
        }
        Set<Long> visited = new HashSet<>();
        ArchiveStorageLocation cursor = parent;
        while (cursor.getParentId() != null) {
            if (!visited.add(cursor.getId()) || cursor.getParentId().equals(currentId)) {
                throw new BadRequestException("位置层级不能形成环");
            }
            cursor = location(cursor.getParentId());
        }
    }

    private ArchiveWarehouse warehouse(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("库房 ID 不合法");
        }
        return warehouseRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "真实库房不存在"));
    }

    private ArchiveWarehouse enabledWarehouse(Long id) {
        ArchiveWarehouse entity = warehouse(id);
        if (!entity.isEnabled()) {
            throw new BadRequestException("真实库房已禁用");
        }
        return entity;
    }

    private ArchiveStorageLocation location(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("位置 ID 不合法");
        }
        return locationRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "存放位置不存在"));
    }

    private String required(@Nullable String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private void requireManagePermission(Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private ArchiveWarehouseResponse toWarehouseResponse(ArchiveWarehouse entity) {
        return new ArchiveWarehouseResponse(
                entity.getId(),
                entity.getWarehouseCode(),
                entity.getWarehouseName(),
                entity.isEnabled(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ArchiveStorageLocationResponse toLocationResponse(ArchiveStorageLocation entity) {
        return new ArchiveStorageLocationResponse(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getParentId(),
                entity.getLocationCode(),
                entity.getLocationName(),
                entity.getLocationType(),
                entity.isEnabled(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public record CreateArchiveWarehouseRequest(
            @Nullable String warehouseCode,
            @Nullable String warehouseName,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record UpdateArchiveWarehouseRequest(
            @Nullable String warehouseCode,
            @Nullable String warehouseName,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveWarehouseResponse(
            Long id,
            String warehouseCode,
            String warehouseName,
            boolean enabled,
            int sortOrder,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {}

    public record CreateArchiveStorageLocationRequest(
            @Nullable Long warehouseId,
            @Nullable Long parentId,
            @Nullable String locationCode,
            @Nullable String locationName,
            @Nullable String locationType,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record UpdateArchiveStorageLocationRequest(
            @Nullable Long warehouseId,
            @Nullable Long parentId,
            boolean parentIdPresent,
            @Nullable String locationCode,
            @Nullable String locationName,
            @Nullable String locationType,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveStorageLocationResponse(
            Long id,
            Long warehouseId,
            @Nullable Long parentId,
            String locationCode,
            String locationName,
            String locationType,
            boolean enabled,
            int sortOrder,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {}
}
