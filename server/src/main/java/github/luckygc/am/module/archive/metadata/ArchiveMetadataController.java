package github.luckygc.am.module.archive.metadata;

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

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryRequest;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldLayoutDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldLayoutRequest;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFieldRequest;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveUniqueConstraintRequest;
import github.luckygc.am.module.archive.record.ArchiveRecordRoutingService;

@RestController
public class ArchiveMetadataController {

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveRecordRoutingService archiveRecordRoutingService;

    public ArchiveMetadataController(
            ArchiveMetadataService archiveMetadataService,
            ArchiveRecordRoutingService archiveRecordRoutingService) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveRecordRoutingService = archiveRecordRoutingService;
    }

    @GetMapping("/api/v1/archive-fonds")
    public CollectionResponse<ArchiveFondsDto> listFonds(Boolean enabled) {
        return CollectionResponse.of(archiveMetadataService.listFonds(enabled));
    }

    @PostMapping("/api/v1/archive-fonds")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFondsDto createFonds(
            @RequestBody ArchiveFondsRequest request, Authentication authentication) {
        return archiveMetadataService.createFonds(request, currentUserId(authentication));
    }

    @PatchMapping("/api/v1/archive-fonds/{id}")
    public ArchiveFondsDto updateFonds(
            @PathVariable Long id,
            @RequestBody ArchiveFondsRequest request,
            Authentication authentication) {
        return archiveMetadataService.updateFonds(id, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-fonds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFonds(@PathVariable Long id, Authentication authentication) {
        archiveMetadataService.deleteFonds(id, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-categories")
    public CollectionResponse<ArchiveCategoryDto> listCategories(Boolean enabled) {
        return CollectionResponse.of(archiveMetadataService.listCategories(enabled));
    }

    @PostMapping("/api/v1/archive-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveCategoryDto createCategory(
            @RequestBody ArchiveCategoryRequest request, Authentication authentication) {
        return archiveMetadataService.createCategory(request, currentUserId(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{id}")
    public ArchiveCategoryDto updateCategory(
            @PathVariable Long id,
            @RequestBody ArchiveCategoryRequest request,
            Authentication authentication) {
        return archiveMetadataService.updateCategory(id, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id, Authentication authentication) {
        archiveMetadataService.deleteCategory(id, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/fields")
    public CollectionResponse<ArchiveFieldDto> listFields(@PathVariable Long categoryId) {
        return CollectionResponse.of(archiveMetadataService.listFields(categoryId));
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFieldDto createField(
            @PathVariable Long categoryId,
            @RequestBody ArchiveFieldRequest request,
            Authentication authentication) {
        return archiveMetadataService.createField(
                categoryId, request, currentUserId(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/fields/{fieldId}")
    public ArchiveFieldDto updateField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            @RequestBody ArchiveFieldRequest request,
            Authentication authentication) {
        return archiveMetadataService.updateField(
                categoryId, fieldId, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-categories/{categoryId}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            Authentication authentication) {
        archiveMetadataService.deleteField(categoryId, fieldId, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto getFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel) {
        return archiveMetadataService.getFieldLayout(categoryId, archiveLevel, surface);
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestBody ArchiveFieldLayoutRequest request,
            Authentication authentication) {
        return archiveMetadataService.savePublicFieldLayout(
                categoryId, archiveLevel, surface, request, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-categories/{id}:buildTable")
    public ArchiveCategoryDto buildTable(
            @PathVariable Long id,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            Authentication authentication) {
        return archiveMetadataService.buildTable(id, archiveLevel, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-categories/{id}:rebuildSearchProjection")
    public ArchiveRecordRoutingService.SearchProjectionRebuildResult rebuildSearchProjection(
            @PathVariable Long id) {
        return archiveRecordRoutingService.rebuildSearchProjection(id);
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
                categoryId, request, currentUserId(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    public ArchiveUniqueConstraintDto updateUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            @RequestBody ArchiveUniqueConstraintRequest request,
            Authentication authentication) {
        return archiveMetadataService.updateUniqueConstraint(
                categoryId, constraintId, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            Authentication authentication) {
        archiveMetadataService.deleteUniqueConstraint(
                categoryId, constraintId, currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
