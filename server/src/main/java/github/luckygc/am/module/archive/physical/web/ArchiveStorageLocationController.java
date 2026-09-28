package github.luckygc.am.module.archive.physical.web;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.ArchiveStorageLocationResponse;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.ArchiveWarehouseResponse;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.CreateArchiveStorageLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.CreateArchiveWarehouseRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveStorageLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveWarehouseRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class ArchiveStorageLocationController {

    private static final Set<String> WAREHOUSE_FIELDS =
            Set.of("warehouseCode", "warehouseName", "enabled", "sortOrder");
    private static final Set<String> LOCATION_FIELDS =
            Set.of(
                    "warehouseId",
                    "parentId",
                    "locationCode",
                    "locationName",
                    "locationType",
                    "enabled",
                    "sortOrder");
    private static final Set<String> WAREHOUSE_READ_ONLY_FIELDS =
            Set.of("id", "createdAt", "updatedAt");
    private static final Set<String> LOCATION_READ_ONLY_FIELDS =
            Set.of("id", "createdAt", "updatedAt");

    private final ArchiveStorageLocationService service;

    public ArchiveStorageLocationController(ArchiveStorageLocationService service) {
        this.service = service;
    }

    @GetMapping("/archive-warehouses")
    public CollectionResponse<ArchiveWarehouseResponse> listWarehouses(@Nullable Boolean enabled) {
        return CollectionResponse.of(service.listWarehouses(enabled));
    }

    @PostMapping("/archive-warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveWarehouseResponse createWarehouse(
            @RequestBody CreateArchiveWarehouseRequest request, Authentication authentication) {
        return service.createWarehouse(request, userId(authentication));
    }

    @PatchMapping(value = "/archive-warehouses/{id}", consumes = "application/merge-patch+json")
    public ArchiveWarehouseResponse updateWarehouse(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        Long operatorUserId = userId(authentication);
        requirePatchObject(request, WAREHOUSE_FIELDS, WAREHOUSE_READ_ONLY_FIELDS);
        return service.updateWarehouse(
                id,
                new UpdateArchiveWarehouseRequest(
                        textField(request, "warehouseCode"),
                        textField(request, "warehouseName"),
                        booleanField(request, "enabled"),
                        integerField(request, "sortOrder")),
                operatorUserId);
    }

    @DeleteMapping("/archive-warehouses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWarehouse(@PathVariable Long id, Authentication authentication) {
        service.deleteWarehouse(id, userId(authentication));
    }

    @GetMapping("/archive-storage-locations")
    public CollectionResponse<ArchiveStorageLocationResponse> listLocations(
            @Nullable Long warehouseId) {
        return CollectionResponse.of(service.listLocations(warehouseId));
    }

    @PostMapping("/archive-storage-locations")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveStorageLocationResponse createLocation(
            @RequestBody CreateArchiveStorageLocationRequest request,
            Authentication authentication) {
        return service.createLocation(request, userId(authentication));
    }

    @PatchMapping(
            value = "/archive-storage-locations/{id}",
            consumes = "application/merge-patch+json")
    public ArchiveStorageLocationResponse updateLocation(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        Long operatorUserId = userId(authentication);
        requirePatchObject(request, LOCATION_FIELDS, LOCATION_READ_ONLY_FIELDS);
        return service.updateLocation(
                id,
                new UpdateArchiveStorageLocationRequest(
                        longField(request, "warehouseId", false),
                        longField(request, "parentId", true),
                        request.has("parentId"),
                        textField(request, "locationCode"),
                        textField(request, "locationName"),
                        textField(request, "locationType"),
                        booleanField(request, "enabled"),
                        integerField(request, "sortOrder")),
                operatorUserId);
    }

    @DeleteMapping("/archive-storage-locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocation(@PathVariable Long id, Authentication authentication) {
        service.deleteLocation(id, userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }

    private void requirePatchObject(
            JsonNode request, Set<String> fields, Set<String> readOnlyFields) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("资源补丁必须是对象");
        }
        for (String field : request.propertyNames()) {
            if (!fields.contains(field)
                    && (readOnlyFields.contains(field) || !request.get(field).isNull())) {
                throw new BadRequestException("不支持修改字段 " + field, field, "字段不可修改");
            }
        }
    }

    private @Nullable String textField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asText();
    }

    private @Nullable Long longField(JsonNode request, String field, boolean removable) {
        JsonNode value = request.get(field);
        if (value == null || (removable && value.isNull())) {
            return null;
        }
        if (value.isNull() || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.longValue();
    }

    private @Nullable Boolean booleanField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isBoolean()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asBoolean();
    }

    private @Nullable Integer integerField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isInt()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.intValue();
    }
}
