package github.luckygc.am.module.archive.item.web;

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

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemListDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemQueryRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemRelationDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemRelationRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemUpdateRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveRelatedFilterCategoryDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.DeleteItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.LockItemRequest;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;

@RestController
public class ArchiveItemController {

    private final ArchiveItemRoutingService archiveItemRoutingService;

    public ArchiveItemController(ArchiveItemRoutingService archiveItemRoutingService) {
        this.archiveItemRoutingService = archiveItemRoutingService;
    }

    @GetMapping("/api/v1/archive-items")
    public ArchiveItemListDto listItems(
            Long categoryId, String fondsCode, Authentication authentication) {
        return archiveItemRoutingService.listItems(
                categoryId, fondsCode, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-items:search")
    public ArchiveItemListDto searchItems(
            @RequestBody ArchiveItemQueryRequest request, Authentication authentication) {
        return archiveItemRoutingService.searchItems(request, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-items:discover")
    public ArchiveItemListDto discoverItems(
            @RequestBody ArchiveItemQueryRequest request, Authentication authentication) {
        return archiveItemRoutingService.discoverItems(request, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-items:searchDeleted")
    public ArchiveItemListDto searchDeletedItems(
            @RequestBody ArchiveItemQueryRequest request, Authentication authentication) {
        return archiveItemRoutingService.searchDeletedItems(request, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-categories/{id}/related-filter-categories")
    public CollectionResponse<ArchiveRelatedFilterCategoryDto> listRelatedFilterCategories(
            @PathVariable Long id) {
        return CollectionResponse.of(archiveItemRoutingService.listRelatedFilterCategories(id));
    }

    @PostMapping("/api/v1/archive-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemDto createItem(
            @RequestBody ArchiveItemRequest request, Authentication authentication) {
        return archiveItemRoutingService.createItem(request, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-items/{id}")
    public ArchiveItemDetailDto getItem(
            @PathVariable Long id,
            @RequestParam(required = false) ArchiveLayoutSurface surface,
            Authentication authentication) {
        return archiveItemRoutingService.getItemDetail(id, currentUserId(authentication), surface);
    }

    @PatchMapping("/api/v1/archive-items/{id}")
    public ArchiveItemDetailDto updateItem(
            @PathVariable Long id,
            @RequestBody ArchiveItemUpdateRequest request,
            Authentication authentication) {
        return archiveItemRoutingService.updateItem(id, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable Long id,
            @RequestBody(required = false) DeleteItemRequest request,
            Authentication authentication) {
        archiveItemRoutingService.deleteItem(id, currentUserId(authentication), request);
    }

    @PostMapping("/api/v1/archive-items/{id}:lock")
    public ArchiveItemDto lockItem(
            @PathVariable Long id,
            @RequestBody(required = false) LockItemRequest request,
            Authentication authentication) {
        return archiveItemRoutingService.lockItem(id, currentUserId(authentication), request);
    }

    @PostMapping("/api/v1/archive-items/{id}:unlock")
    public ArchiveItemDto unlockItem(@PathVariable Long id, Authentication authentication) {
        return archiveItemRoutingService.unlockItem(id, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-items/{id}/relations")
    public CollectionResponse<ArchiveItemRelationDto> listRelations(
            @PathVariable Long id, @RequestParam(defaultValue = "1") Integer depth) {
        return CollectionResponse.of(archiveItemRoutingService.listRelations(id, depth));
    }

    @PostMapping("/api/v1/archive-items/{id}/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemRelationDto createRelation(
            @PathVariable Long id,
            @RequestBody ArchiveItemRelationRequest request,
            Authentication authentication) {
        return archiveItemRoutingService.createRelation(id, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-items/{id}/relations/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelation(
            @PathVariable Long id, @PathVariable Long relationId, Authentication authentication) {
        archiveItemRoutingService.deleteRelation(id, relationId, currentUserId(authentication));
    }

    private @Nullable Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
