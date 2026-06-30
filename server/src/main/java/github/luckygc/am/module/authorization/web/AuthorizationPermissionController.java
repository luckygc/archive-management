package github.luckygc.am.module.authorization.web;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService.PermissionDefinition;

@RestController
public class AuthorizationPermissionController {

    private final AuthorizationPermissionService permissionService;

    public AuthorizationPermissionController(AuthorizationPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/authorization-permissions")
    public CollectionResponse<PermissionDefinition> listPermissions(
            @Nullable Authentication authentication) {
        permissionService.requirePermission(
                currentUserId(authentication),
                AuthorizationPermissionCode.AUTHORIZATION_PERMISSION_MANAGE);
        return CollectionResponse.of(permissionService.listPermissionCatalog());
    }

    @GetMapping("/api/v1/me/permissions")
    public CurrentUserPermissionsResponse listCurrentUserPermissions(
            @Nullable Authentication authentication) {
        Long userId = currentUserId(authentication);
        return new CurrentUserPermissionsResponse(
                permissionService.listUserPermissionCodes(userId));
    }

    @GetMapping("/api/v1/authorization-roles/{role}/permissions")
    public RolePermissionsResponse listRolePermissions(
            @PathVariable Long role, @Nullable Authentication authentication) {
        permissionService.requirePermission(
                currentUserId(authentication),
                AuthorizationPermissionCode.AUTHORIZATION_PERMISSION_MANAGE);
        return new RolePermissionsResponse(role, permissionService.listRolePermissionCodes(role));
    }

    @PutMapping("/api/v1/authorization-roles/{role}/permissions")
    public RolePermissionsResponse saveRolePermissions(
            @PathVariable Long role,
            @RequestBody UpdateRolePermissionsRequest request,
            @Nullable Authentication authentication) {
        permissionService.requirePermission(
                currentUserId(authentication),
                AuthorizationPermissionCode.AUTHORIZATION_PERMISSION_MANAGE);
        permissionService.saveRolePermissions(role, request.permissionCodes());
        return new RolePermissionsResponse(role, permissionService.listRolePermissionCodes(role));
    }

    private Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    public record CurrentUserPermissionsResponse(List<String> permissionCodes) {}

    public record RolePermissionsResponse(Long roleId, List<String> permissionCodes) {}

    public record UpdateRolePermissionsRequest(List<String> permissionCodes) {}
}
