package github.luckygc.am.module.archive.item.web;

import jakarta.data.page.PageRequest;

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
import github.luckygc.am.common.api.RawRequestStrings;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService.DeleteItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService.UpdateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService.LockItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.ArchiveItemListDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.ArchiveRelatedFilterCategoryDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService.ArchiveItemRelationDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService.ArchiveItemRelationRequest;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;

@RestController
public class ArchiveItemController {

    private final ArchiveItemCommandService archiveItemRoutingService;
    private final ArchiveItemQueryService archiveItemQueryService;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveItemRelationService archiveItemRelationService;
    private final ArchiveItemLockService archiveItemLockService;

    public ArchiveItemController(
            ArchiveItemCommandService archiveItemRoutingService,
            ArchiveItemQueryService archiveItemQueryService,
            ArchiveItemReadService archiveItemReadService,
            ArchiveItemRelationService archiveItemRelationService,
            ArchiveItemLockService archiveItemLockService) {
        this.archiveItemRoutingService = archiveItemRoutingService;
        this.archiveItemQueryService = archiveItemQueryService;
        this.archiveItemReadService = archiveItemReadService;
        this.archiveItemRelationService = archiveItemRelationService;
        this.archiveItemLockService = archiveItemLockService;
    }

    @GetMapping("/api/v1/archive-items")
    public ArchiveItemListDto listItems(
            Long categoryId, String fondsCode, Authentication authentication) {
        return archiveItemQueryService.listItems(
                categoryId,
                fondsCode,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-items:search")
    public ArchiveItemListDto searchItems(
            @RawRequestStrings @RequestBody SearchArchiveItemsRequest request,
            PageRequest page,
            Authentication authentication) {
        return archiveItemQueryService.searchItems(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    @PostMapping("/api/v1/archive-items:discover")
    public ArchiveItemListDto discoverItems(
            @RawRequestStrings @RequestBody SearchArchiveItemsRequest request,
            PageRequest page,
            Authentication authentication) {
        return archiveItemQueryService.discoverItems(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    @PostMapping("/api/v1/archive-items:searchDeleted")
    public ArchiveItemListDto searchDeletedItems(
            @RawRequestStrings @RequestBody SearchArchiveItemsRequest request,
            PageRequest page,
            Authentication authentication) {
        return archiveItemQueryService.searchDeletedItems(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    @GetMapping("/api/v1/archive-categories/{id}/related-filter-categories")
    public CollectionResponse<ArchiveRelatedFilterCategoryDto> listRelatedFilterCategories(
            @PathVariable Long id) {
        return CollectionResponse.of(archiveItemQueryService.listRelatedFilterCategories(id));
    }

    @PostMapping("/api/v1/archive-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemDto createItem(
            @RequestBody CreateArchiveItemRequest request, Authentication authentication) {
        return archiveItemRoutingService.createItem(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/archive-items/{id}")
    public ArchiveItemDetailDto getItem(
            @PathVariable Long id,
            @RequestParam(required = false) ArchiveLayoutSurface surface,
            Authentication authentication) {
        return archiveItemReadService.getItemDetail(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                surface);
    }

    @PatchMapping("/api/v1/archive-items/{id}")
    public ArchiveItemDetailDto updateItem(
            @PathVariable Long id,
            @RequestBody UpdateArchiveItemRequest request,
            Authentication authentication) {
        return archiveItemRoutingService.updateItem(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @DeleteMapping("/api/v1/archive-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable Long id,
            @RequestBody(required = false) DeleteItemRequest request,
            Authentication authentication) {
        archiveItemRoutingService.deleteItem(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                request);
    }

    @PostMapping("/api/v1/archive-items/{id}:lock")
    public ArchiveItemDto lockItem(
            @PathVariable Long id,
            @RequestBody(required = false) LockItemRequest request,
            Authentication authentication) {
        return archiveItemLockService.lockItem(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                request);
    }

    @PostMapping("/api/v1/archive-items/{id}:unlock")
    public ArchiveItemDto unlockItem(@PathVariable Long id, Authentication authentication) {
        return archiveItemLockService.unlockItem(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/archive-items/{id}/relations")
    public CollectionResponse<ArchiveItemRelationDto> listRelations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer depth,
            Authentication authentication) {
        return CollectionResponse.of(
                archiveItemRelationService.listRelations(
                        id,
                        depth,
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal())));
    }

    @PostMapping("/api/v1/archive-items/{id}/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemRelationDto createRelation(
            @PathVariable Long id,
            @RequestBody ArchiveItemRelationRequest request,
            Authentication authentication) {
        return archiveItemRelationService.createRelation(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @DeleteMapping("/api/v1/archive-items/{id}/relations/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelation(
            @PathVariable Long id, @PathVariable Long relationId, Authentication authentication) {
        archiveItemRelationService.deleteRelation(
                id,
                relationId,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }
}
