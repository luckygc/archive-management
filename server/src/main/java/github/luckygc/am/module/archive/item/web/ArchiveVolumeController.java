package github.luckygc.am.module.archive.item.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.AddItemToVolumeRequest;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.ArchiveVolumeDto;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.CreateArchiveVolumeRequest;

@RestController
public class ArchiveVolumeController {

    private final ArchiveVolumeService archiveVolumeService;

    public ArchiveVolumeController(ArchiveVolumeService archiveVolumeService) {
        this.archiveVolumeService = archiveVolumeService;
    }

    @GetMapping("/api/v1/archive-volumes")
    public CollectionResponse<ArchiveVolumeDto> listVolumes(
            String fondsCode, String categoryCode, Authentication authentication) {
        return CollectionResponse.of(
                archiveVolumeService.listVolumes(
                        fondsCode,
                        categoryCode,
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    @PostMapping("/api/v1/archive-volumes")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveVolumeDto createVolume(
            @RequestBody CreateArchiveVolumeRequest request, Authentication authentication) {
        return archiveVolumeService.createVolume(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/archive-volumes/{id}")
    public ArchiveVolumeDto getVolume(@PathVariable Long id, Authentication authentication) {
        return archiveVolumeService.getVolume(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-volumes/{id}:addItem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItemToVolume(
            @PathVariable Long id,
            @RequestBody AddItemToVolumeRequest request,
            Authentication authentication) {
        archiveVolumeService.addItemToVolume(
                id,
                request.itemId(),
                request.displayOrder(),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }
}
