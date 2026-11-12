package github.luckygc.am.module.authentication.web;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.ServiceAccountStatus;
import github.luckygc.am.module.authentication.service.ServiceAccountManagementService;
import github.luckygc.am.module.authentication.service.ServiceAccountManagementService.CreateServiceAccountRequest;
import github.luckygc.am.module.authentication.service.ServiceAccountManagementService.ReplaceServiceAccountRolesRequest;
import github.luckygc.am.module.authentication.service.ServiceAccountManagementService.ServiceAccountResponse;
import github.luckygc.am.module.authentication.service.ServiceAccountManagementService.UpdateServiceAccountRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class ServiceAccountController {

    private static final Set<String> PATCH_FIELDS =
            Set.of("name", "description", "ownerUserId", "status");

    private final ServiceAccountManagementService serviceAccountService;

    public ServiceAccountController(ServiceAccountManagementService serviceAccountService) {
        this.serviceAccountService = serviceAccountService;
    }

    @GetMapping("/service-accounts")
    public CursorPageResponse<ServiceAccountResponse> list(
            @RequestParam(required = false) @Nullable ServiceAccountStatus status,
            PageRequest page,
            @Nullable Authentication authentication) {
        return serviceAccountService.list(status, page, userId(authentication));
    }

    @PostMapping("/service-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceAccountResponse create(
            @RequestBody CreateServiceAccountRequest request,
            @Nullable Authentication authentication) {
        return serviceAccountService.create(request, userId(authentication));
    }

    @GetMapping("/service-accounts/{id}")
    public ServiceAccountResponse get(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return serviceAccountService.get(id, userId(authentication));
    }

    @PatchMapping(value = "/service-accounts/{id}", consumes = "application/merge-patch+json")
    public ServiceAccountResponse update(
            @PathVariable Long id,
            @RequestBody JsonNode request,
            @Nullable Authentication authentication) {
        return serviceAccountService.update(id, toUpdateRequest(request), userId(authentication));
    }

    @PutMapping("/service-accounts/{id}/roles")
    public ServiceAccountResponse replaceRoles(
            @PathVariable Long id,
            @RequestBody ReplaceServiceAccountRolesRequest request,
            @Nullable Authentication authentication) {
        return serviceAccountService.replaceRoles(id, request.roleIds(), userId(authentication));
    }

    @DeleteMapping("/service-accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @Nullable Authentication authentication) {
        serviceAccountService.delete(id, userId(authentication));
    }

    private UpdateServiceAccountRequest toUpdateRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("服务账号补丁必须是对象");
        }
        for (String fieldName : request.propertyNames()) {
            if (!PATCH_FIELDS.contains(fieldName)) {
                throw new BadRequestException("不支持修改字段 " + fieldName, fieldName, "字段不可修改");
            }
        }
        return new UpdateServiceAccountRequest(
                text(request, "name"),
                request.has("description"),
                text(request, "description"),
                longValue(request, "ownerUserId"),
                status(request));
    }

    private @Nullable String text(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asText();
    }

    private @Nullable Long longValue(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asLong();
    }

    private @Nullable ServiceAccountStatus status(JsonNode request) {
        JsonNode value = request.get("status");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BadRequestException("status 不合法", "status", "status 不合法");
        }
        try {
            return ServiceAccountStatus.valueOf(value.asText());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("status 不合法", "status", "status 不合法");
        }
    }

    private Long userId(@Nullable Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
