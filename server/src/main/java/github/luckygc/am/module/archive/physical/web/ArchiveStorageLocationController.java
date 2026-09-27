package github.luckygc.am.module.archive.physical.web;

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
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.ArchiveStorageLocationResponse;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.ArchiveWarehouseResponse;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.CreateArchiveStorageLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.CreateArchiveWarehouseRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveStorageLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveWarehouseRequest;

@RestController
public class ArchiveStorageLocationController {

    private final ArchiveStorageLocationService service;

    public ArchiveStorageLocationController(ArchiveStorageLocationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/archive-warehouses")
    public CollectionResponse<ArchiveWarehouseResponse> listWarehouses(@Nullable Boolean enabled) {
        return CollectionResponse.of(service.listWarehouses(enabled));
    }

    @PostMapping("/api/v1/archive-warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveWarehouseResponse createWarehouse(
            @RequestBody CreateArchiveWarehouseRequest request, Authentication authentication) {
        return service.createWarehouse(request, userId(authentication));
    }

    @PatchMapping("/api/v1/archive-warehouses/{id}")
    public ArchiveWarehouseResponse updateWarehouse(
            @PathVariable Long id,
            @RequestBody UpdateArchiveWarehouseRequest request,
            Authentication authentication) {
        return service.updateWarehouse(id, request, userId(authentication));
    }

    @DeleteMapping("/api/v1/archive-warehouses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWarehouse(@PathVariable Long id, Authentication authentication) {
        service.deleteWarehouse(id, userId(authentication));
    }

    @GetMapping("/api/v1/archive-storage-locations")
    public CollectionResponse<ArchiveStorageLocationResponse> listLocations(
            @Nullable Long warehouseId) {
        return CollectionResponse.of(service.listLocations(warehouseId));
    }

    @PostMapping("/api/v1/archive-storage-locations")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveStorageLocationResponse createLocation(
            @RequestBody CreateArchiveStorageLocationRequest request,
            Authentication authentication) {
        return service.createLocation(request, userId(authentication));
    }

    @PatchMapping("/api/v1/archive-storage-locations/{id}")
    public ArchiveStorageLocationResponse updateLocation(
            @PathVariable Long id,
            @RequestBody UpdateArchiveStorageLocationRequest request,
            Authentication authentication) {
        return service.updateLocation(id, request, userId(authentication));
    }

    @DeleteMapping("/api/v1/archive-storage-locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocation(@PathVariable Long id, Authentication authentication) {
        service.deleteLocation(id, userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
