package github.luckygc.am.module.archive.item.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService.OperationMonitor;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionRebuildService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveItemSearchProjectionRebuildController {

    private final ArchiveItemSearchProjectionRebuildService rebuildService;
    private final ArchiveItemSearchProjectionOperationService operationService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemSearchProjectionRebuildController(
            ArchiveItemSearchProjectionRebuildService rebuildService,
            ArchiveItemSearchProjectionOperationService operationService,
            AuthorizationPermissionService permissionService) {
        this.rebuildService = rebuildService;
        this.operationService = operationService;
        this.permissionService = permissionService;
    }

    @PostMapping("/archive-categories/{categoryId}:rebuildSearchProjection")
    public ResponseEntity<OperationMonitor> startRebuild(
            @PathVariable Long categoryId,
            Authentication authentication,
            HttpServletRequest request) {
        Long userId = requireMetadataManage(authentication);
        Long jobId = rebuildService.start(categoryId, userId);
        String operationId = ArchiveItemSearchProjectionOperationService.operationId(jobId);
        URI operationLocation =
                URI.create(request.getRequestURL().toString())
                        .resolve("/operations/" + operationId);
        return ResponseEntity.accepted()
                .header("Operation-Id", operationId)
                .header("Operation-Location", operationLocation.toString())
                .header("Retry-After", "5")
                .body(operationService.get(operationId, userId));
    }

    private Long requireMetadataManage(Authentication authentication) {
        Long userId = AuthenticatedUsers.requireUserId(authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
        return userId;
    }
}
