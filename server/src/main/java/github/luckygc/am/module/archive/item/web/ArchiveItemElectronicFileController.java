package github.luckygc.am.module.archive.item.web;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemElectronicFileResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemFileDownload;

@RestController
public class ArchiveItemElectronicFileController {

    private final ArchiveItemElectronicFileService electronicFileService;

    public ArchiveItemElectronicFileController(
            ArchiveItemElectronicFileService electronicFileService) {
        this.electronicFileService = electronicFileService;
    }

    @GetMapping("/api/v1/archive-items/{archiveItem}/electronic-files")
    public CollectionResponse<ArchiveItemElectronicFileResponse> listFiles(
            @PathVariable Long archiveItem) {
        return electronicFileService.listFiles(archiveItem);
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

    @GetMapping("/api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}/content")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable Long archiveItem, @PathVariable Long electronicFile) {
        ArchiveItemFileDownload download =
                electronicFileService.downloadFile(archiveItem, electronicFile);
        FileStorageResource resource = download.resource();
        String contentType =
                org.apache.commons.lang3.StringUtils.defaultIfBlank(
                        resource.contentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(resource.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new InputStreamResource(download.resource().inputStream()));
    }

    private @Nullable Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        return null;
    }
}
