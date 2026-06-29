package github.luckygc.am.module.archive.item;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.ArchiveItemLineTableService.ArchiveItemLineFieldDto;
import github.luckygc.am.module.archive.item.ArchiveItemLineTableService.ArchiveItemLineFieldRequest;
import github.luckygc.am.module.archive.item.ArchiveItemLineTableService.ArchiveItemLineTableDto;
import github.luckygc.am.module.archive.item.ArchiveItemLineTableService.ArchiveItemLineTableRequest;

@RestController
public class ArchiveItemLineTableController {

    private final ArchiveItemLineTableService archiveItemLineTableService;

    public ArchiveItemLineTableController(ArchiveItemLineTableService archiveItemLineTableService) {
        this.archiveItemLineTableService = archiveItemLineTableService;
    }

    @GetMapping("/api/v1/archive-categories/{categoryId}/item-line-tables")
    public CollectionResponse<ArchiveItemLineTableDto> listLineTables(
            @PathVariable Long categoryId) {
        return CollectionResponse.of(archiveItemLineTableService.listLineTables(categoryId));
    }

    @PostMapping("/api/v1/archive-categories/{categoryId}/item-line-tables")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemLineTableDto createLineTable(
            @PathVariable Long categoryId,
            @RequestBody ArchiveItemLineTableRequest request,
            Authentication authentication) {
        return archiveItemLineTableService.createLineTable(
                categoryId, request, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-item-line-tables/{lineTableId}")
    public ArchiveItemLineTableDto getLineTable(@PathVariable Long lineTableId) {
        return archiveItemLineTableService.getLineTable(lineTableId);
    }

    @PostMapping("/api/v1/archive-item-line-tables/{lineTableId}:build")
    public ArchiveItemLineTableDto buildLineTable(
            @PathVariable Long lineTableId, Authentication authentication) {
        return archiveItemLineTableService.buildLineTable(
                lineTableId, currentUserId(authentication));
    }

    @GetMapping("/api/v1/archive-item-line-tables/{lineTableId}/fields")
    public CollectionResponse<ArchiveItemLineFieldDto> listLineFields(
            @PathVariable Long lineTableId) {
        return CollectionResponse.of(archiveItemLineTableService.listLineFields(lineTableId));
    }

    @PostMapping("/api/v1/archive-item-line-tables/{lineTableId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemLineFieldDto createLineField(
            @PathVariable Long lineTableId,
            @RequestBody ArchiveItemLineFieldRequest request,
            Authentication authentication) {
        return archiveItemLineTableService.createLineField(
                lineTableId, request, currentUserId(authentication));
    }

    private @Nullable Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
