package github.luckygc.am.module.authorization.web;

import org.jspecify.annotations.Nullable;
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

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.AuthorizationRoleDto;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.CreateAuthorizationRoleRequest;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.UpdateAuthorizationRoleRequest;

@RestController
public class AuthorizationRoleManagementController {

    private final AuthorizationRoleManagementService roleService;

    public AuthorizationRoleManagementController(AuthorizationRoleManagementService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/api/v1/authorization-roles")
    public CursorPageResponse<AuthorizationRoleDto> listRoles(
            @RequestParam(required = false) @Nullable Boolean enabled,
            CursorPageRequest page,
            @Nullable Authentication authentication) {
        return roleService.listRoles(
                enabled,
                page,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/authorization-roles")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizationRoleDto createRole(
            @RequestBody CreateAuthorizationRoleRequest request,
            @Nullable Authentication authentication) {
        return roleService.createRole(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/authorization-roles/{id}")
    public AuthorizationRoleDto getRoleDetail(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return roleService.getRole(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PatchMapping("/api/v1/authorization-roles/{id}")
    public AuthorizationRoleDto updateRole(
            @PathVariable Long id,
            @RequestBody UpdateAuthorizationRoleRequest request,
            @Nullable Authentication authentication) {
        return roleService.updateRole(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @DeleteMapping("/api/v1/authorization-roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable Long id, @Nullable Authentication authentication) {
        roleService.deleteRole(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }
}
