package github.luckygc.am.module.archive.item.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

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
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService;
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

    @PostMapping("/archive-categories/{categoryId}:rebuildSearchProjection")
    public ResponseEntity<JobAcceptedResponse> startRebuild(
            @PathVariable Long categoryId,
            Authentication authentication,
            HttpServletRequest request) {
        Long userId = requireMetadataManage(authentication);
        JobAcceptedResponse response = rebuildService.start(categoryId, userId);
        String operationId =
                ArchiveItemSearchProjectionOperationService.operationId(response.jobId());
        URI operationLocation =
                URI.create(request.getRequestURL().toString())
                        .resolve("/operations/" + operationId);
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, response.operationLocation())
                .header("Operation-Id", operationId)
                .header("Operation-Location", operationLocation.toString())
                .header("Retry-After", "5")
                .body(response);
    }

    @GetMapping("/archive-search-projection-rebuild-jobs/{jobId}")
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
