package github.luckygc.am.module.archive;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.infrastructure.security.ArchiveUserDetails;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveCategoryRequest;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFieldLayoutDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFieldLayoutRequest;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFieldRequest;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFondsDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveFondsRequest;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.ArchiveMetadataService.ArchiveUniqueConstraintRequest;

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
    public List<ArchiveFondsDto> listFonds(Boolean enabled) {
        return archiveMetadataService.listFonds(enabled);
    }

    @PostMapping("/api/v1/archive-fonds")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFondsDto createFonds(@RequestBody ArchiveFondsRequest request) {
        return archiveMetadataService.createFonds(request);
    }

    @PatchMapping("/api/v1/archive-fonds/{id}")
    public ArchiveFondsDto updateFonds(
            @PathVariable Long id, @RequestBody ArchiveFondsRequest request) {
        return archiveMetadataService.updateFonds(id, request);
    }

    @DeleteMapping("/api/v1/archive-fonds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFonds(@PathVariable Long id, Authentication authentication) {
        archiveMetadataService.deleteFonds(id, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-categories")
    public List<ArchiveCategoryDto> listCategories(Boolean enabled) {
        return archiveMetadataService.listCategories(enabled);
    }

    @PostMapping("/api/v1/archive-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveCategoryDto createCategory(@RequestBody ArchiveCategoryRequest request) {
        return archiveMetadataService.createCategory(request);
    }

    @PatchMapping("/api/v1/archive-categories/{id}")
    public ArchiveCategoryDto updateCategory(
            @PathVariable Long id, @RequestBody ArchiveCategoryRequest request) {
        return archiveMetadataService.updateCategory(id, request);
    }

    @DeleteMapping("/api/v1/archive-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id, Authentication authentication) {
        archiveMetadataService.deleteCategory(id, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/fields")
    public List<ArchiveFieldDto> listFields(@PathVariable Long categoryId) {
        return archiveMetadataService.listFields(categoryId);
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFieldDto createField(
            @PathVariable Long categoryId, @RequestBody ArchiveFieldRequest request) {
        return archiveMetadataService.createField(categoryId, request);
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/fields/{fieldId}")
    public ArchiveFieldDto updateField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            @RequestBody ArchiveFieldRequest request) {
        return archiveMetadataService.updateField(categoryId, fieldId, request);
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
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            Authentication authentication) {
        return archiveMetadataService.getFieldLayout(categoryId, archiveLevel, surface, scope, currentUserId(authentication));
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestBody ArchiveFieldLayoutRequest request) {
        return archiveMetadataService.savePublicFieldLayout(categoryId, archiveLevel, surface, request);
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/layouts/{surface}:saveMyLayout")
    public ArchiveFieldLayoutDto saveMyFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestBody ArchiveFieldLayoutRequest request,
            Authentication authentication) {
        return archiveMetadataService.saveMyFieldLayout(categoryId, archiveLevel, surface, request, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-categories/{id}:buildTable")
    public ArchiveCategoryDto buildTable(@PathVariable Long id, @RequestParam(required = false) ArchiveLevel archiveLevel) {
        return archiveMetadataService.buildTable(id, archiveLevel);
    }

    @PostMapping("/api/v1/archive-categories/{id}:rebuildSearchProjection")
    public ArchiveRecordRoutingService.SearchProjectionRebuildResult rebuildSearchProjection(@PathVariable Long id) {
        return archiveRecordRoutingService.rebuildSearchProjection(id);
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/unique-constraints")
    public List<ArchiveUniqueConstraintDto> listUniqueConstraints(@PathVariable Long categoryId) {
        return archiveMetadataService.listUniqueConstraints(categoryId);
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/unique-constraints")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveUniqueConstraintDto createUniqueConstraint(
            @PathVariable Long categoryId, @RequestBody ArchiveUniqueConstraintRequest request) {
        return archiveMetadataService.createUniqueConstraint(categoryId, request);
    }

    @PatchMapping("/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    public ArchiveUniqueConstraintDto updateUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            @RequestBody ArchiveUniqueConstraintRequest request) {
        return archiveMetadataService.updateUniqueConstraint(categoryId, constraintId, request);
    }

    @DeleteMapping("/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniqueConstraint(@PathVariable Long categoryId, @PathVariable Long constraintId) {
        archiveMetadataService.deleteUniqueConstraint(categoryId, constraintId);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ArchiveUserDetails userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
