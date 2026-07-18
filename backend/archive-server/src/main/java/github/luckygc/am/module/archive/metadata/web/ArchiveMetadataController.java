package github.luckygc.am.module.archive.metadata.web;

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
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionService.SearchProjectionRebuildResult;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveClassificationSchemeDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveClassificationSchemeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldLayoutDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldLayoutRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveRetentionPeriodDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveSecurityLevelDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveRetentionPeriodRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveSecurityLevelRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveMetadataController {

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveFondsService archiveFondsService;
    private final ArchiveItemSearchProjectionService searchProjectionService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveMetadataController(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveFondsService archiveFondsService,
            ArchiveItemSearchProjectionService searchProjectionService,
            AuthorizationPermissionService permissionService) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveFondsService = archiveFondsService;
        this.searchProjectionService = searchProjectionService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/archive-fonds")
    public CollectionResponse<ArchiveFondsDto> listFonds(Boolean enabled) {
        return CollectionResponse.of(archiveFondsService.listFonds(enabled));
    }

    @PostMapping("/api/v1/archive-fonds")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFondsDto createFonds(
            @RequestBody ArchiveFondsRequest request, Authentication authentication) {
        return archiveFondsService.createFonds(request, requireMetadataManage(authentication));
    }

    @PatchMapping("/api/v1/archive-fonds/{id}")
    public ArchiveFondsDto updateFonds(
            @PathVariable Long id,
            @RequestBody ArchiveFondsRequest request,
            Authentication authentication) {
        return archiveFondsService.updateFonds(id, request, requireMetadataManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-fonds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFonds(@PathVariable Long id, Authentication authentication) {
        archiveFondsService.deleteFonds(id, requireMetadataManage(authentication));
    }

    @GetMapping("/api/v1/archive-fonds/{fondsCode}/category-scopes")
    public CollectionResponse<ArchiveFondsCategoryScopeDto> listFondsCategoryScopes(
            @PathVariable String fondsCode) {
        return CollectionResponse.of(archiveCategoryService.listFondsCategoryScopes(fondsCode));
    }

    @PutMapping("/api/v1/archive-fonds/{fondsCode}/category-scopes")
    public CollectionResponse<ArchiveFondsCategoryScopeDto> saveFondsCategoryScopes(
            @PathVariable String fondsCode,
            @RequestBody java.util.List<ArchiveFondsCategoryScopeRequest> requests,
            Authentication authentication) {
        return CollectionResponse.of(
                archiveCategoryService.saveFondsCategoryScopes(
                        fondsCode, requests, requireMetadataManage(authentication)));
    }

    @GetMapping("/api/v1/archive-fonds/{fondsCode}/categories")
    public CollectionResponse<ArchiveCategoryDto> listCategoriesForFonds(
            @PathVariable String fondsCode, Boolean enabled) {
        return CollectionResponse.of(
                archiveCategoryService.listCategoriesForFonds(fondsCode, enabled));
    }

    @GetMapping("/api/v1/archive-classification-schemes")
    public CollectionResponse<ArchiveClassificationSchemeDto> listClassificationSchemes(
            Boolean enabled) {
        return CollectionResponse.of(
                archiveMetadataReferenceService.listClassificationSchemes(enabled));
    }

    @PostMapping("/api/v1/archive-classification-schemes")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveClassificationSchemeDto createClassificationScheme(
            @RequestBody ArchiveClassificationSchemeRequest request,
            Authentication authentication) {
        return archiveMetadataReferenceService.createClassificationScheme(
                request, requireMetadataManage(authentication));
    }

    @PatchMapping("/api/v1/archive-classification-schemes/{id}")
    public ArchiveClassificationSchemeDto updateClassificationScheme(
            @PathVariable Long id,
            @RequestBody ArchiveClassificationSchemeRequest request,
            Authentication authentication) {
        return archiveMetadataReferenceService.updateClassificationScheme(
                id, request, requireMetadataManage(authentication));
    }

    @GetMapping("/api/v1/archive-security-levels")
    public CollectionResponse<ArchiveSecurityLevelDto> listSecurityLevels(Boolean enabled) {
        return CollectionResponse.of(archiveMetadataReferenceService.listSecurityLevels(enabled));
    }

    @PatchMapping("/api/v1/archive-security-levels/{id}")
    public ArchiveSecurityLevelDto updateSecurityLevel(
            @PathVariable Long id,
            @RequestBody UpdateArchiveSecurityLevelRequest request,
            Authentication authentication) {
        requireMetadataManage(authentication);
        return archiveMetadataReferenceService.updateSecurityLevel(id, request);
    }

    @GetMapping("/api/v1/archive-retention-periods")
    public CollectionResponse<ArchiveRetentionPeriodDto> listRetentionPeriods(Boolean enabled) {
        return CollectionResponse.of(archiveMetadataReferenceService.listRetentionPeriods(enabled));
    }

    @PatchMapping("/api/v1/archive-retention-periods/{id}")
    public ArchiveRetentionPeriodDto updateRetentionPeriod(
            @PathVariable Long id,
            @RequestBody UpdateArchiveRetentionPeriodRequest request,
            Authentication authentication) {
        requireMetadataManage(authentication);
        return archiveMetadataReferenceService.updateRetentionPeriod(id, request);
    }

    @GetMapping("/api/v1/archive-categories")
    public CollectionResponse<ArchiveCategoryDto> listCategories(Boolean enabled) {
        return CollectionResponse.of(archiveCategoryService.listCategories(enabled));
    }

    @PostMapping("/api/v1/archive-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveCategoryDto createCategory(
            @RequestBody ArchiveCategoryRequest request, Authentication authentication) {
        return archiveCategoryService.createCategory(
                request, requireMetadataManage(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{id}")
    public ArchiveCategoryDto updateCategory(
            @PathVariable Long id,
            @RequestBody ArchiveCategoryRequest request,
            Authentication authentication) {
        return archiveCategoryService.updateCategory(
                id, request, requireMetadataManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id, Authentication authentication) {
        archiveCategoryService.deleteCategory(id, requireMetadataManage(authentication));
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/fields")
    public CollectionResponse<ArchiveFieldDto> listFields(
            @PathVariable Long categoryId,
            @RequestParam(required = false) ArchiveLevel archiveLevel) {
        return CollectionResponse.of(
                archiveLevel == null
                        ? archiveMetadataService.listFields(categoryId)
                        : archiveMetadataService.listFields(categoryId, archiveLevel));
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFieldDto createField(
            @PathVariable Long categoryId,
            @RequestBody ArchiveFieldRequest request,
            Authentication authentication) {
        return archiveMetadataService.createField(
                categoryId, request, requireMetadataManage(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/fields/{fieldId}")
    public ArchiveFieldDto updateField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            @RequestBody ArchiveFieldRequest request,
            Authentication authentication) {
        return archiveMetadataService.updateField(
                categoryId, fieldId, request, requireMetadataManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-categories/{categoryId}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            Authentication authentication) {
        archiveMetadataService.deleteField(
                categoryId, fieldId, requireMetadataManage(authentication));
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto getFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestParam(required = false) ArchiveFieldScope fieldScope) {
        return archiveMetadataService.getFieldLayout(categoryId, archiveLevel, fieldScope, surface);
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestParam(required = false) ArchiveFieldScope fieldScope,
            @RequestBody ArchiveFieldLayoutRequest request,
            Authentication authentication) {
        return archiveMetadataService.savePublicFieldLayout(
                categoryId,
                archiveLevel,
                fieldScope,
                surface,
                request,
                requireMetadataManage(authentication));
    }

    @PostMapping("/api/v1/archive-categories/{id}:buildTable")
    public ArchiveCategoryDto buildTable(
            @PathVariable Long id,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestParam(required = false) ArchiveFieldScope fieldScope,
            Authentication authentication) {
        return archiveMetadataService.buildTable(
                id, archiveLevel, fieldScope, requireMetadataManage(authentication));
    }

    @PostMapping("/api/v1/archive-categories/{id}:rebuildSearchProjection")
    public SearchProjectionRebuildResult rebuildSearchProjection(
            @PathVariable Long id, Authentication authentication) {
        requireMetadataManage(authentication);
        return searchProjectionService.rebuild(id);
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/unique-constraints")
    public CollectionResponse<ArchiveUniqueConstraintDto> listUniqueConstraints(
            @PathVariable Long categoryId) {
        return CollectionResponse.of(archiveMetadataService.listUniqueConstraints(categoryId));
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/unique-constraints")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveUniqueConstraintDto createUniqueConstraint(
            @PathVariable Long categoryId,
            @RequestBody ArchiveUniqueConstraintRequest request,
            Authentication authentication) {
        return archiveMetadataService.createUniqueConstraint(
                categoryId, request, requireMetadataManage(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    public ArchiveUniqueConstraintDto updateUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            @RequestBody ArchiveUniqueConstraintRequest request,
            Authentication authentication) {
        return archiveMetadataService.updateUniqueConstraint(
                categoryId, constraintId, request, requireMetadataManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            Authentication authentication) {
        archiveMetadataService.deleteUniqueConstraint(
                categoryId, constraintId, requireMetadataManage(authentication));
    }

    private Long requireMetadataManage(Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
        return userId;
    }
}
