package github.luckygc.am.module.archive.governance.web;

import java.util.List;

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

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.ArchiveGovernanceBindingResponse;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.ArchiveGovernanceSchemeResponse;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.ArchiveGovernanceSchemeVersionResponse;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.ArchiveGovernanceScopeResponse;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.CreateArchiveGovernanceBindingRequest;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.CreateArchiveGovernanceSchemeRequest;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.CreateArchiveGovernanceSchemeVersionRequest;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.CreateArchiveGovernanceScopeRequest;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.UpdateArchiveGovernanceSchemeRequest;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService.UpdateArchiveGovernanceSchemeVersionRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveGovernanceController {

    private final ArchiveGovernanceService governanceService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveGovernanceController(
            ArchiveGovernanceService governanceService,
            AuthorizationPermissionService permissionService) {
        this.governanceService = governanceService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/archive-governance-schemes")
    public CollectionResponse<ArchiveGovernanceSchemeResponse> listSchemes(Boolean enabled) {
        return CollectionResponse.of(governanceService.listSchemes(enabled));
    }

    @GetMapping("/api/v1/archive-governance-schemes/{schemeId}")
    public ArchiveGovernanceSchemeResponse getScheme(@PathVariable Long schemeId) {
        return governanceService.getScheme(schemeId);
    }

    @PostMapping("/api/v1/archive-governance-schemes")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveGovernanceSchemeResponse createScheme(
            @RequestBody CreateArchiveGovernanceSchemeRequest request,
            Authentication authentication) {
        return governanceService.createScheme(request, requireManage(authentication));
    }

    @PatchMapping("/api/v1/archive-governance-schemes/{schemeId}")
    public ArchiveGovernanceSchemeResponse updateScheme(
            @PathVariable Long schemeId,
            @RequestBody UpdateArchiveGovernanceSchemeRequest request,
            Authentication authentication) {
        return governanceService.updateScheme(schemeId, request, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-governance-schemes/{schemeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScheme(@PathVariable Long schemeId, Authentication authentication) {
        governanceService.deleteScheme(schemeId, requireManage(authentication));
    }

    @GetMapping("/api/v1/archive-governance-schemes/{schemeId}/versions")
    public CollectionResponse<ArchiveGovernanceSchemeVersionResponse> listVersions(
            @PathVariable Long schemeId) {
        return CollectionResponse.of(governanceService.listVersions(schemeId));
    }

    @PostMapping("/api/v1/archive-governance-schemes/{schemeId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveGovernanceSchemeVersionResponse createVersion(
            @PathVariable Long schemeId,
            @RequestBody CreateArchiveGovernanceSchemeVersionRequest request,
            Authentication authentication) {
        return governanceService.createVersion(schemeId, request, requireManage(authentication));
    }

    @PatchMapping("/api/v1/archive-governance-scheme-versions/{versionId}")
    public ArchiveGovernanceSchemeVersionResponse updateVersion(
            @PathVariable Long versionId,
            @RequestBody UpdateArchiveGovernanceSchemeVersionRequest request,
            Authentication authentication) {
        return governanceService.updateVersion(versionId, request, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-governance-scheme-versions/{versionId}:publish")
    public ArchiveGovernanceSchemeVersionResponse publishVersion(
            @PathVariable Long versionId, Authentication authentication) {
        return governanceService.publishVersion(versionId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-governance-scheme-versions/{versionId}:freeze")
    public ArchiveGovernanceSchemeVersionResponse freezeVersion(
            @PathVariable Long versionId, Authentication authentication) {
        return governanceService.freezeVersion(versionId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-governance-scheme-versions/{versionId}:retire")
    public ArchiveGovernanceSchemeVersionResponse retireVersion(
            @PathVariable Long versionId, Authentication authentication) {
        return governanceService.retireVersion(versionId, requireManage(authentication));
    }

    @GetMapping("/api/v1/archive-governance-scheme-versions:resolveDefault")
    public ArchiveGovernanceSchemeVersionResponse resolveDefaultVersion(
            @RequestParam(required = false) String fondsCode,
            @RequestParam(required = false) String categoryCode) {
        return governanceService.resolveDefaultVersion(fondsCode, categoryCode);
    }

    @GetMapping("/api/v1/archive-governance-scheme-versions/{versionId}/scopes")
    public CollectionResponse<ArchiveGovernanceScopeResponse> listScopes(
            @PathVariable Long versionId) {
        return CollectionResponse.of(governanceService.listScopes(versionId));
    }

    @PutMapping("/api/v1/archive-governance-scheme-versions/{versionId}/scopes")
    public CollectionResponse<ArchiveGovernanceScopeResponse> replaceScopes(
            @PathVariable Long versionId,
            @RequestBody List<CreateArchiveGovernanceScopeRequest> requests,
            Authentication authentication) {
        return CollectionResponse.of(
                governanceService.replaceScopes(versionId, requests, requireManage(authentication)));
    }

    @GetMapping("/api/v1/archive-governance-scheme-versions/{versionId}/bindings")
    public CollectionResponse<ArchiveGovernanceBindingResponse> listBindings(
            @PathVariable Long versionId) {
        return CollectionResponse.of(governanceService.listBindings(versionId));
    }

    @PutMapping("/api/v1/archive-governance-scheme-versions/{versionId}/bindings")
    public CollectionResponse<ArchiveGovernanceBindingResponse> replaceBindings(
            @PathVariable Long versionId,
            @RequestBody List<CreateArchiveGovernanceBindingRequest> requests,
            Authentication authentication) {
        return CollectionResponse.of(
                governanceService.replaceBindings(
                        versionId, requests, requireManage(authentication)));
    }

    private Long requireManage(Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        permissionService.requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_GOVERNANCE_MANAGE);
        return userId;
    }
}
