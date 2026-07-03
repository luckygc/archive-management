package github.luckygc.am.module.authentication.web;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.AuthenticationUserDetailDto;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.AuthenticationUserDto;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.CreateAuthenticationUserRequest;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.ResetPasswordRequest;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.RoleSummary;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.SaveUserRolesRequest;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.UpdateAuthenticationUserRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class AuthenticationUserManagementController {

    private final AuthenticationUserManagementService userService;

    public AuthenticationUserManagementController(AuthenticationUserManagementService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/v1/authentication-users")
    public CursorPageResponse<AuthenticationUserDto> listUsers(
            @RequestParam(required = false) @Nullable String keyword,
            CursorPageRequest page,
            @Nullable Authentication authentication) {
        return userService.listUsers(
                keyword,
                page,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/authentication-users")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticationUserDto createUser(
            @RequestBody CreateAuthenticationUserRequest request,
            @Nullable Authentication authentication) {
        return userService.createUser(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/authentication-users/{id}")
    public AuthenticationUserDetailDto getUserDetail(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return userService.getUserDetail(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PatchMapping("/api/v1/authentication-users/{id}")
    public AuthenticationUserDto updateUser(
            @PathVariable Long id,
            @RequestBody JsonNode request,
            @Nullable Authentication authentication) {
        return userService.updateUser(
                id,
                toUpdateRequest(request),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/authentication-users/{id}:resetPassword")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable Long id,
            @RequestBody ResetPasswordRequest request,
            @Nullable Authentication authentication) {
        userService.resetPassword(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/authentication-users/{id}/roles")
    public CollectionResponse<RoleSummary> listUserRoles(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return CollectionResponse.of(
                userService.listUserRoles(
                        id,
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    @PutMapping("/api/v1/authentication-users/{id}/roles")
    public CollectionResponse<RoleSummary> saveUserRoles(
            @PathVariable Long id,
            @RequestBody SaveUserRolesRequest request,
            @Nullable Authentication authentication) {
        return CollectionResponse.of(
                userService.saveUserRoles(
                        id,
                        request,
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    private UpdateAuthenticationUserRequest toUpdateRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("请求体不能为空");
        }
        String displayName = nullableText(request, "displayName");
        String email = nullableText(request, "email");
        String mobilePhone = nullableText(request, "mobilePhone");
        Boolean enabled = nullableBoolean(request, "enabled");
        if (request.has("departmentId")) {
            return new UpdateAuthenticationUserRequest(
                    displayName,
                    email,
                    mobilePhone,
                    enabled,
                    nullableLong(request, "departmentId"));
        }
        return UpdateAuthenticationUserRequest.withoutDepartmentChange(
                displayName, email, mobilePhone, enabled);
    }

    private @Nullable String nullableText(JsonNode request, String fieldName) {
        JsonNode value = request.get(fieldName);
        if (value == null) {
            return null;
        }
        if (value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new BadRequestException(fieldName + "不合法", fieldName, fieldName + "不合法");
        }
        return value.asText();
    }

    private @Nullable Long nullableLong(JsonNode request, String fieldName) {
        JsonNode value = request.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new BadRequestException(fieldName + "不合法", fieldName, fieldName + "不合法");
        }
        return value.asLong();
    }

    private @Nullable Boolean nullableBoolean(JsonNode request, String fieldName) {
        JsonNode value = request.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new BadRequestException(fieldName + "不合法", fieldName, fieldName + "不合法");
        }
        return value.asBoolean();
    }
}
