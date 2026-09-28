package github.luckygc.am.module.authorization.web;

import java.util.Set;

import jakarta.data.page.PageRequest;

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

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.AuthorizationRoleDto;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.CreateAuthorizationRoleRequest;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.UpdateAuthorizationRoleRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class AuthorizationRoleManagementController {

    private static final Set<String> PATCH_FIELDS = Set.of("roleName", "description", "enabled");
    private static final Set<String> READ_ONLY_FIELDS = Set.of("id", "createdAt");

    private final AuthorizationRoleManagementService roleService;

    public AuthorizationRoleManagementController(AuthorizationRoleManagementService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/authorization-roles")
    public CursorPageResponse<AuthorizationRoleDto> listRoles(
            @RequestParam(required = false) @Nullable Boolean enabled,
            PageRequest page,
            @Nullable Authentication authentication) {
        return roleService.listRoles(
                enabled,
                page,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/authorization-roles")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizationRoleDto createRole(
            @RequestBody CreateAuthorizationRoleRequest request,
            @Nullable Authentication authentication) {
        return roleService.createRole(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/authorization-roles/{id}")
    public AuthorizationRoleDto getRoleDetail(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return roleService.getRole(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PatchMapping(value = "/authorization-roles/{id}", consumes = "application/merge-patch+json")
    public AuthorizationRoleDto updateRole(
            @PathVariable Long id,
            @RequestBody JsonNode request,
            @Nullable Authentication authentication) {
        Long operatorUserId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        return roleService.updateRole(id, toUpdateRequest(request), operatorUserId);
    }

    private UpdateAuthorizationRoleRequest toUpdateRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("角色补丁必须是对象");
        }
        for (String fieldName : request.propertyNames()) {
            if (!PATCH_FIELDS.contains(fieldName)
                    && (READ_ONLY_FIELDS.contains(fieldName) || !request.get(fieldName).isNull())) {
                throw new BadRequestException("不支持修改字段 " + fieldName, fieldName, "字段不可修改");
            }
        }
        return new UpdateAuthorizationRoleRequest(
                textField(request, "roleName", false),
                request.has("description"),
                textField(request, "description", true),
                booleanField(request, "enabled"));
    }

    private @Nullable String textField(JsonNode request, String field, boolean removable) {
        JsonNode value = request.get(field);
        if (value == null || (value.isNull() && removable)) {
            return null;
        }
        if (value.isNull() || !value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asText();
    }

    private @Nullable Boolean booleanField(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isBoolean()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asBoolean();
    }

    @DeleteMapping("/authorization-roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable Long id, @Nullable Authentication authentication) {
        roleService.deleteRole(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }
}
