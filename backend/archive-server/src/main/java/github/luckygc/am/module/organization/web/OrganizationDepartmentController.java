package github.luckygc.am.module.organization.web;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.CreateOrganizationDepartmentRequest;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.UpdateOrganizationDepartmentRequest;

import tools.jackson.databind.JsonNode;

@RestController
public class OrganizationDepartmentController {

    private final OrganizationDepartmentService departmentService;
    private final AuthorizationPermissionService permissionService;

    public OrganizationDepartmentController(
            OrganizationDepartmentService departmentService,
            AuthorizationPermissionService permissionService) {
        this.departmentService = departmentService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/organization-departments")
    public CollectionResponse<OrganizationDepartmentResponse> listDepartments(
            @RequestParam(required = false) @Nullable Boolean enabled,
            @Nullable Authentication authentication) {
        requireReadPermission(authentication);
        return CollectionResponse.of(departmentService.listDepartments(enabled));
    }

    @GetMapping("/api/v1/organization-departments/{organizationDepartment}")
    public OrganizationDepartmentResponse getDepartment(
            @PathVariable Long organizationDepartment, @Nullable Authentication authentication) {
        requireReadPermission(authentication);
        return departmentService.getDepartment(organizationDepartment);
    }

    @PostMapping("/api/v1/organization-departments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDepartmentResponse createDepartment(
            @RequestBody CreateOrganizationDepartmentRequest request,
            @Nullable Authentication authentication) {
        requireManagePermission(authentication);
        return departmentService.createDepartment(request);
    }

    @PatchMapping("/api/v1/organization-departments/{organizationDepartment}")
    public OrganizationDepartmentResponse updateDepartment(
            @PathVariable Long organizationDepartment,
            @RequestBody JsonNode request,
            @Nullable Authentication authentication) {
        requireManagePermission(authentication);
        return departmentService.updateDepartment(organizationDepartment, toUpdateRequest(request));
    }

    private void requireManagePermission(@Nullable Authentication authentication) {
        permissionService.requirePermission(
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE);
    }

    private void requireReadPermission(@Nullable Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        if (permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.ORGANIZATION_DEPARTMENT_MANAGE.code())
                || permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE.code())
                || permissionService.hasPermission(
                        userId, AuthorizationPermissionCode.ARCHIVE_DATA_SCOPE_MANAGE.code())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
    }

    private UpdateOrganizationDepartmentRequest toUpdateRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("请求体不能为空");
        }
        String departmentCode = nullableText(request, "departmentCode");
        String departmentName = nullableText(request, "departmentName");
        Boolean enabled = nullableBoolean(request, "enabled");
        Integer sortOrder = nullableInteger(request, "sortOrder");
        if (request.has("parentId")) {
            return new UpdateOrganizationDepartmentRequest(
                    departmentCode,
                    departmentName,
                    nullableLong(request, "parentId"),
                    enabled,
                    sortOrder);
        }
        return UpdateOrganizationDepartmentRequest.withoutParentChange(
                departmentCode, departmentName, enabled, sortOrder);
    }

    private @Nullable String nullableText(JsonNode request, String fieldName) {
        JsonNode value = request.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
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

    private @Nullable Integer nullableInteger(JsonNode request, String fieldName) {
        JsonNode value = request.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new BadRequestException(fieldName + "不合法", fieldName, fieldName + "不合法");
        }
        return value.asInt();
    }
}
