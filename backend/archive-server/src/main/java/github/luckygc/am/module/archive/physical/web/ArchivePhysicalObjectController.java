package github.luckygc.am.module.archive.physical.web;

import java.util.List;

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
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalLocationHistoryResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalObjectResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.BatchAssignArchiveLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.BatchAssignArchiveLocationResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.CreateArchivePhysicalObjectRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.UpdateArchivePhysicalObjectRequest;

@RestController
public class ArchivePhysicalObjectController {

    private final ArchivePhysicalObjectService service;

    public ArchivePhysicalObjectController(ArchivePhysicalObjectService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/archive-physical-objects")
    public CollectionResponse<ArchivePhysicalObjectResponse> findByOwner(
            @Nullable Long archiveItemId,
            @Nullable Long archiveVolumeId,
            Authentication authentication) {
        ArchivePhysicalObjectResponse response =
                service.findByOwner(archiveItemId, archiveVolumeId, userId(authentication));
        return CollectionResponse.of(response == null ? List.of() : List.of(response));
    }

    @PostMapping("/api/v1/archive-physical-objects")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchivePhysicalObjectResponse create(
            @RequestBody CreateArchivePhysicalObjectRequest request,
            Authentication authentication) {
        return service.create(request, userId(authentication));
    }

    @GetMapping("/api/v1/archive-physical-objects/{id}")
    public ArchivePhysicalObjectResponse get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, userId(authentication));
    }

    @PatchMapping("/api/v1/archive-physical-objects/{id}")
    public ArchivePhysicalObjectResponse update(
            @PathVariable Long id,
            @RequestBody UpdateArchivePhysicalObjectRequest request,
            Authentication authentication) {
        return service.update(id, request, userId(authentication));
    }

    @DeleteMapping("/api/v1/archive-physical-objects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, userId(authentication));
    }

    @PostMapping("/api/v1/archive-physical-objects:batchAssignLocation")
    public BatchAssignArchiveLocationResponse batchAssignLocation(
            @RequestBody BatchAssignArchiveLocationRequest request, Authentication authentication) {
        return service.batchAssignLocation(request, userId(authentication));
    }

    @GetMapping("/api/v1/archive-physical-objects/{id}/location-history")
    public CollectionResponse<ArchivePhysicalLocationHistoryResponse> listLocationHistory(
            @PathVariable Long id, Authentication authentication) {
        return CollectionResponse.of(service.listLocationHistory(id, userId(authentication)));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
