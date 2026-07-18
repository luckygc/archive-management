package github.luckygc.am.module.archive.item.web;

import java.io.IOException;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileLinkService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.UploadArchiveItemElectronicFileCommand;

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

    @PostMapping(
            value = "/api/v1/archive-items/{archiveItem}/electronic-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveItemElectronicFileResponse uploadFile(
            @PathVariable Long archiveItem,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String usageType,
            @RequestParam(required = false) Integer displayOrder,
            Authentication authentication) {
        try (java.io.InputStream inputStream = file.getInputStream()) {
            return electronicFileService.uploadFile(
                    archiveItem,
                    new UploadArchiveItemElectronicFileCommand(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            inputStream,
                            usageType,
                            displayOrder),
                    currentUserId(authentication));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件读取失败", exception);
        }
    }

    @DeleteMapping("/api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(
            @PathVariable Long archiveItem,
            @PathVariable Long electronicFile,
            Authentication authentication) {
        electronicFileService.deleteFile(
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
