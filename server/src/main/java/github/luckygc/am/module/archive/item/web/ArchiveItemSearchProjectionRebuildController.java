package github.luckygc.am.module.archive.item.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.JobAcceptedResponse;
import github.luckygc.am.common.api.JobStatusResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionRebuildService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveItemSearchProjectionRebuildController {

    private final ArchiveItemSearchProjectionRebuildService rebuildService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemSearchProjectionRebuildController(
            ArchiveItemSearchProjectionRebuildService rebuildService,
            AuthorizationPermissionService permissionService) {
        this.rebuildService = rebuildService;
        this.permissionService = permissionService;
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}:rebuildSearchProjection")
    public ResponseEntity<JobAcceptedResponse> startRebuild(
            @PathVariable Long categoryId, Authentication authentication) {
        Long userId = requireMetadataManage(authentication);
        JobAcceptedResponse response = rebuildService.start(categoryId, userId);
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, response.operationLocation())
                .header("Operation-Location", response.operationLocation())
                .body(response);
    }

    @GetMapping("/api/v1/archive-search-projection-rebuild-jobs/{jobId}")
    public JobStatusResponse getRebuildJob(
            @PathVariable Long jobId, Authentication authentication) {
        requireMetadataManage(authentication);
        return rebuildService.get(jobId);
    }

    private Long requireMetadataManage(Authentication authentication) {
        Long userId = AuthenticatedUsers.requireUserId(authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
        return userId;
    }
}
