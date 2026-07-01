package github.luckygc.am.module.archive.item.web;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileLinkService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse;

@RestController
public class ArchiveItemElectronicFileController {

    private final ArchiveItemElectronicFileService electronicFileService;
    private final ArchiveItemElectronicFileLinkService electronicFileLinkService;

    public ArchiveItemElectronicFileController(
            ArchiveItemElectronicFileService electronicFileService,
            ArchiveItemElectronicFileLinkService electronicFileLinkService) {
        this.electronicFileService = electronicFileService;
        this.electronicFileLinkService = electronicFileLinkService;
    }

    @GetMapping("/api/v1/archive-items/{archiveItem}/electronic-files")
    public CollectionResponse<ArchiveItemElectronicFileResponse> listFiles(
            @PathVariable Long archiveItem, Authentication authentication) {
        return electronicFileService.listFiles(archiveItem, currentUserId(authentication));
    }

    @PostMapping("/api/v1/archive-items/{archiveItem}/electronic-files")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemElectronicFileResponse bindFile(
            @PathVariable Long archiveItem,
            @RequestBody ArchiveItemElectronicFileRequest request,
            Authentication authentication) {
        return electronicFileService.bindFile(archiveItem, request, currentUserId(authentication));
    }

    @DeleteMapping("/api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindFile(
            @PathVariable Long archiveItem,
            @PathVariable Long electronicFile,
            Authentication authentication) {
        electronicFileService.unbindFile(
                archiveItem, electronicFile, currentUserId(authentication));
    }

    @PostMapping(
            "/api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}:createDownloadLink")
    public ArchiveItemElectronicFileDownloadLinkResponse createDownloadLink(
            @PathVariable Long archiveItem,
            @PathVariable Long electronicFile,
            Authentication authentication) {
        ArchiveItemElectronicFileLinkService.DownloadLinkCreated created =
                electronicFileLinkService.createDownloadLink(
                        archiveItem, electronicFile, currentUserId(authentication));
        return new ArchiveItemElectronicFileDownloadLinkResponse(
                "/api/v1/file-links/" + created.code() + ":download", created.expiresAt());
    }

    private Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    public record ArchiveItemElectronicFileDownloadLinkResponse(
            String url, LocalDateTime expiresAt) {}
}
