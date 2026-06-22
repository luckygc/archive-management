package github.luckygc.am.module.archive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.infrastructure.security.ArchiveUserDetails;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordDetailDto;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordDto;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordListDto;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordQueryRequest;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordRequest;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordUpdateRequest;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.DeleteRecordRequest;
import github.luckygc.am.module.archive.ArchiveRecordRoutingService.LockRecordRequest;

@RestController
public class ArchiveRecordController {

    private final ArchiveRecordRoutingService archiveRecordRoutingService;

    public ArchiveRecordController(ArchiveRecordRoutingService archiveRecordRoutingService) {
        this.archiveRecordRoutingService = archiveRecordRoutingService;
    }

    @GetMapping("/api/v1/archive-records")
    public ArchiveRecordListDto listRecords(Long categoryId, String fondsCode, Authentication authentication) {
        return archiveRecordRoutingService.listRecords(categoryId, fondsCode, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-records:search")
    public ArchiveRecordListDto searchRecords(@RequestBody ArchiveRecordQueryRequest request, Authentication authentication) {
        return archiveRecordRoutingService.searchRecords(request, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-records")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRecordDto createRecord(@RequestBody ArchiveRecordRequest request, Authentication authentication) {
        return archiveRecordRoutingService.createRecord(request, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-records/{id}")
    public ArchiveRecordDetailDto getRecord(
            @PathVariable Long id,
            @RequestParam(required = false) ArchiveLayoutSurface surface,
            Authentication authentication) {
        return archiveRecordRoutingService.getRecordDetail(id, currentUserId(authentication), surface);
    }

    @PatchMapping("/api/v1/archive-records/{id}")
    public ArchiveRecordDetailDto updateRecord(
            @PathVariable Long id,
            @RequestBody ArchiveRecordUpdateRequest request,
            Authentication authentication) {
        return archiveRecordRoutingService.updateRecord(id, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecord(
            @PathVariable Long id,
            @RequestBody(required = false) DeleteRecordRequest request,
            Authentication authentication) {
        archiveRecordRoutingService.deleteRecord(id, currentUserId(authentication), request);
    }

    @PostMapping("/api/v1/archive-records/{id}:lock")
    public ArchiveRecordDto lockRecord(
            @PathVariable Long id,
            @RequestBody(required = false) LockRecordRequest request,
            Authentication authentication) {
        return archiveRecordRoutingService.lockRecord(id, currentUserId(authentication), request);
    }

    @PostMapping("/api/v1/archive-records/{id}:unlock")
    public ArchiveRecordDto unlockRecord(@PathVariable Long id, Authentication authentication) {
        return archiveRecordRoutingService.unlockRecord(id, currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ArchiveUserDetails userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
