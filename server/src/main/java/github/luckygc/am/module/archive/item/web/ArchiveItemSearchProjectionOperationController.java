package github.luckygc.am.module.archive.item.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService.OperationMonitor;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionOperationService.OperationPage;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveItemSearchProjectionOperationController {

    private static final String RETRY_AFTER_SECONDS = "5";

    private final ArchiveItemSearchProjectionOperationService operationService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemSearchProjectionOperationController(
            ArchiveItemSearchProjectionOperationService operationService,
            AuthorizationPermissionService permissionService) {
        this.operationService = operationService;
        this.permissionService = permissionService;
    }

    @GetMapping("/operations")
    public OperationsPageResponse listOperations(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) @Nullable String cursor,
            Authentication authentication,
            HttpServletRequest request) {
        Long userId = requireMetadataManage(authentication);
        OperationPage page = operationService.list(userId, limit, cursor);
        return new OperationsPageResponse(
                page.items(),
                pageUrl(request, limit, cursor),
                page.prevCursor() == null ? null : pageUrl(request, limit, page.prevCursor()),
                page.nextCursor() == null ? null : pageUrl(request, limit, page.nextCursor()));
    }

    @GetMapping("/operations/{id}")
    public ResponseEntity<OperationMonitor> getOperation(
            @PathVariable String id, Authentication authentication) {
        Long userId = requireMetadataManage(authentication);
        OperationMonitor monitor = operationService.get(id, userId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if ("NotStarted".equals(monitor.status()) || "Running".equals(monitor.status())) {
            response.header("Retry-After", RETRY_AFTER_SECONDS);
        }
        return response.body(monitor);
    }

    private Long requireMetadataManage(Authentication authentication) {
        Long userId = AuthenticatedUsers.requireUserId(authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
        return userId;
    }

    private String pageUrl(HttpServletRequest request, int limit, @Nullable String cursor) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                        .replaceQuery(null)
                        .queryParam("limit", limit);
        if (cursor != null && !cursor.isBlank()) {
            builder.queryParam("cursor", cursor);
        }
        return builder.build().toUriString();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OperationsPageResponse(
            List<OperationMonitor> items,
            String self,
            @Nullable String prev,
            @Nullable String next) {}
}
