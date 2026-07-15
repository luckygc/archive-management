package github.luckygc.am.module.archive.item.web;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import github.luckygc.am.common.api.RawRequestStrings;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveImportResult;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.DownloadLinkCreated;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.SearchArchiveItemsRequest;

@RestController
public class ArchiveItemImportExportController {

    private final ArchiveItemImportExportService importExportService;

    public ArchiveItemImportExportController(ArchiveItemImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    @PostMapping(
            "/api/v1/archive-categories/{categoryId}/archive-items:createImportTemplateDownloadLink")
    public ArchiveItemDownloadLinkResponse createImportTemplateDownloadLink(
            @PathVariable Long categoryId, Authentication authentication) {
        return toResponse(
                importExportService.createImportTemplateDownloadLink(
                        categoryId, currentUserId(authentication)));
    }

    @PostMapping(
            path = "/api/v1/archive-categories/{categoryId}/archive-items:import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ArchiveImportResult importItems(
            @PathVariable Long categoryId,
            @RequestParam MultipartFile file,
            Authentication authentication)
            throws IOException {
        return importExportService.importItems(
                categoryId,
                file.getInputStream(),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-items:createExportDownloadLink")
    public ArchiveItemDownloadLinkResponse createExportDownloadLink(
            @RawRequestStrings @RequestBody(required = false) SearchArchiveItemsRequest request,
            Authentication authentication) {
        return toResponse(
                importExportService.createExportDownloadLink(
                        request, currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }

    private ArchiveItemDownloadLinkResponse toResponse(DownloadLinkCreated link) {
        return new ArchiveItemDownloadLinkResponse(
                "/api/v1/file-links/" + link.code() + ":download", link.expiresAt());
    }

    public record ArchiveItemDownloadLinkResponse(String url, LocalDateTime expiresAt) {}
}
