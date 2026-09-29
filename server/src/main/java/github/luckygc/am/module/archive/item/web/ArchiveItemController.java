package github.luckygc.am.module.archive.item.web;

import java.util.List;

import jakarta.data.page.PageRequest;

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
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.RawRequestStrings;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService.LockItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService.ArchiveItemRelationRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService.ArchiveItemRelationResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveItemListDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveItemRelatedGroupRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveItemWhereRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveRelatedFilterCategoryDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemService;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.DeleteItemRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.ReassignArchiveItemFondsRequest;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;

import tools.jackson.databind.JsonNode;

@RestController
public class ArchiveItemController {

    private final ArchiveItemService archiveItemService;
    private final ArchiveItemSearchService archiveItemSearchService;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveItemRelationService archiveItemRelationService;
    private final ArchiveItemLockService archiveItemLockService;

    public ArchiveItemController(
            ArchiveItemService archiveItemService,
            ArchiveItemSearchService archiveItemSearchService,
            ArchiveItemReadService archiveItemReadService,
            ArchiveItemRelationService archiveItemRelationService,
            ArchiveItemLockService archiveItemLockService) {
        this.archiveItemService = archiveItemService;
        this.archiveItemSearchService = archiveItemSearchService;
        this.archiveItemReadService = archiveItemReadService;
        this.archiveItemRelationService = archiveItemRelationService;
        this.archiveItemLockService = archiveItemLockService;
    }

    @GetMapping("/archive-items")
    public ArchiveItemListDto listItems(
            @Nullable Long categoryId,
            @Nullable String fondsCode,
            PageRequest page,
            Authentication authentication) {
        return archiveItemSearchService.listItems(
                categoryId,
                fondsCode,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    @PostMapping("/archive-items:search")
    public ArchiveItemListDto searchItems(
            @RawRequestStrings @RequestBody SearchArchiveItemsBody request,
            @RequestParam(required = false) @Nullable String sort,
            PageRequest page,
            Authentication authentication) {
        return archiveItemSearchService.searchItems(
                request.toSearchRequest(sort),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    @PostMapping("/archive-items:discover")
    public ArchiveItemListDto discoverItems(
            @RawRequestStrings @RequestBody SearchArchiveItemsBody request,
            @RequestParam(required = false) @Nullable String sort,
            PageRequest page,
            Authentication authentication) {
        return archiveItemSearchService.discoverItems(
                request.toSearchRequest(sort),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    @PostMapping("/archive-items:searchDeleted")
    public ArchiveItemListDto searchDeletedItems(
            @RawRequestStrings @RequestBody SearchArchiveItemsBody request,
            PageRequest page,
            Authentication authentication) {
        return archiveItemSearchService.searchDeletedItems(
                request.toSearchRequest(null),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                page);
    }

    public record SearchArchiveItemsBody(
            @Nullable Long categoryId,
            @Nullable String fondsCode,
            @Nullable String keyword,
            @Nullable ArchiveItemWhereRequest where,
            @Nullable List<@Nullable ArchiveItemRelatedGroupRequest> relatedGroups,
            @Nullable Long volumeId) {

        private SearchArchiveItemsRequest toSearchRequest(@Nullable String sort) {
            return new SearchArchiveItemsRequest(
                    categoryId,
                    fondsCode,
                    keyword,
                    where,
                    relatedGroups,
                    null,
                    null,
                    ArchiveItemSortQuery.parse(sort),
                    volumeId);
        }
    }

    @GetMapping("/archive-categories/{id}/related-filter-categories")
    public CollectionResponse<ArchiveRelatedFilterCategoryDto> listRelatedFilterCategories(
            @PathVariable Long id) {
        return CollectionResponse.of(archiveItemSearchService.listRelatedFilterCategories(id));
    }

    @PostMapping("/archive-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemDto createItem(
            @RequestBody CreateArchiveItemRequest request, Authentication authentication) {
        return archiveItemService.createItem(
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/archive-items/{id}")
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

    @PatchMapping(value = "/archive-items/{id}", consumes = "application/merge-patch+json")
    public ArchiveItemDetailDto updateItem(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        return archiveItemService.patchItem(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/archive-items/{id}:reassignFonds")
    public ArchiveItemDetailDto reassignItemFonds(
            @PathVariable Long id,
            @RequestBody ReassignArchiveItemFondsRequest request,
            Authentication authentication) {
        return archiveItemService.reassignFonds(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @DeleteMapping("/archive-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable Long id,
            @RequestBody(required = false) DeleteItemRequest request,
            Authentication authentication) {
        archiveItemService.deleteItem(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()),
                request);
    }

    @PostMapping("/archive-items/{id}:lock")
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

    @PostMapping("/archive-items/{id}:unlock")
    public ArchiveItemDto unlockItem(@PathVariable Long id, Authentication authentication) {
        return archiveItemLockService.unlockItem(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/archive-items/{id}/relations")
    public CursorPageResponse<ArchiveItemRelationResponse> listRelations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer depth,
            PageRequest page,
            Authentication authentication) {
        return archiveItemRelationService.listRelations(
                id,
                depth,
                page,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/archive-items/{id}/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemRelationResponse createRelation(
            @PathVariable Long id,
            @RequestBody(required = false) ArchiveItemRelationRequest request,
            Authentication authentication) {
        return archiveItemRelationService.createRelation(
                id,
                request,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @DeleteMapping("/archive-items/{id}/relations/{relationId}")
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
