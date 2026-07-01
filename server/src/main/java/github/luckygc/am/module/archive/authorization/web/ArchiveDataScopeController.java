package github.luckygc.am.module.archive.authorization.web;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ArchiveDataScopeResponse;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.CreateArchiveDataScopeRequest;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.RoleArchiveDataScopesResponse;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.UpdateArchiveDataScopeRequest;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.UserArchiveDataScopesResponse;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveDataScopeController {

    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveMetadataService archiveMetadataService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveDataScopeController(
            ArchiveDataScopeService dataScopeService,
            ArchiveMetadataService archiveMetadataService,
            AuthorizationPermissionService permissionService) {
        this.dataScopeService = dataScopeService;
        this.archiveMetadataService = archiveMetadataService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/archive-data-scopes")
    public CollectionResponse<ArchiveDataScopeResponse> listScopes(
            @RequestParam(defaultValue = "true") boolean enabled,
            @Nullable Authentication authentication) {
        requirePermission(authentication);
        return CollectionResponse.of(dataScopeService.listScopes(enabled));
    }

    @PostMapping("/api/v1/archive-data-scopes")
    public ArchiveDataScopeResponse createScope(
            @RequestBody CreateArchiveDataScopeRequest request,
            @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.createScope(request);
    }

    @GetMapping("/api/v1/archive-data-scopes/{archiveDataScope}")
    public ArchiveDataScopeResponse getScope(
            @PathVariable Long archiveDataScope, @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.getScope(archiveDataScope);
    }

    @PutMapping("/api/v1/archive-data-scopes/{archiveDataScope}")
    public ArchiveDataScopeResponse updateScope(
            @PathVariable Long archiveDataScope,
            @RequestBody UpdateArchiveDataScopeRequest request,
            @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.updateScope(archiveDataScope, request);
    }

    @GetMapping("/api/v1/authorization-roles/{role}/archive-data-scopes")
    public RoleArchiveDataScopesResponse listRoleDataScopes(
            @PathVariable Long role, @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.listRoleDataScopes(role);
    }

    @PutMapping("/api/v1/authorization-roles/{role}/archive-data-scopes")
    public RoleArchiveDataScopesResponse saveRoleDataScopes(
            @PathVariable Long role,
            @RequestBody UpdateRoleArchiveDataScopesRequest request,
            @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.saveRoleDataScopes(role, request.scopeIds());
    }

    @GetMapping("/api/v1/authorization-users/{user}/archive-data-scopes")
    public UserArchiveDataScopesResponse listUserDataScopes(
            @PathVariable Long user, @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.listUserDataScopes(user);
    }

    @PutMapping("/api/v1/authorization-users/{user}/archive-data-scopes")
    public UserArchiveDataScopesResponse saveUserDataScopes(
            @PathVariable Long user,
            @RequestBody UpdateUserArchiveDataScopesRequest request,
            @Nullable Authentication authentication) {
        requirePermission(authentication);
        return dataScopeService.saveUserDataScopes(user, request.scopeIds());
    }

    @GetMapping("/api/v1/archive-categories/{archiveCategory}/data-scope-fields")
    public CollectionResponse<ArchiveFieldDto> listDataScopeFields(
            @PathVariable Long archiveCategory,
            @RequestParam(defaultValue = "ITEM") ArchiveLevel archiveLevel,
            @Nullable Authentication authentication) {
        requirePermission(authentication);
        return CollectionResponse.of(
                archiveMetadataService.listEnabledFields(archiveCategory, archiveLevel).stream()
                        .filter(ArchiveFieldDto::dataScopeFilterable)
                        .toList());
    }

    public record UpdateRoleArchiveDataScopesRequest(List<Long> scopeIds) {}

    public record UpdateUserArchiveDataScopesRequest(List<Long> scopeIds) {}

    private void requirePermission(@Nullable Authentication authentication) {
        permissionService.requirePermission(
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                AuthorizationPermissionCode.ARCHIVE_DATA_SCOPE_MANAGE);
    }
}
