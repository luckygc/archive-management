package github.luckygc.am.module.archive.item;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.ArchiveVolumeService.AddItemToVolumeRequest;
import github.luckygc.am.module.archive.item.ArchiveVolumeService.ArchiveVolumeDto;
import github.luckygc.am.module.archive.item.ArchiveVolumeService.ArchiveVolumeRequest;

@RestController
public class ArchiveVolumeController {

    private final ArchiveVolumeService archiveVolumeService;

    public ArchiveVolumeController(ArchiveVolumeService archiveVolumeService) {
        this.archiveVolumeService = archiveVolumeService;
    }

    @GetMapping("/api/v1/archive-volumes")
    public CollectionResponse<ArchiveVolumeDto> listVolumes(String fondsCode, String categoryCode) {
        return CollectionResponse.of(archiveVolumeService.listVolumes(fondsCode, categoryCode));
    }

    @PostMapping("/api/v1/archive-volumes")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveVolumeDto createVolume(
            @RequestBody ArchiveVolumeRequest request, Authentication authentication) {
        return archiveVolumeService.createVolume(request, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-volumes/{id}")
    public ArchiveVolumeDto getVolume(@PathVariable Long id) {
        return archiveVolumeService.getVolume(id);
    }

    @PostMapping("/api/v1/archive-volumes/{id}:addItem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItemToVolume(
            @PathVariable Long id,
            @RequestBody AddItemToVolumeRequest request,
            Authentication authentication) {
        archiveVolumeService.addItemToVolume(
                id, request.itemId(), request.displayOrder(), currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
