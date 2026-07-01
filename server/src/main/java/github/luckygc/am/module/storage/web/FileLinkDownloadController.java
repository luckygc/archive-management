package github.luckygc.am.module.storage.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.FileLinkService.FileLinkTarget;
import github.luckygc.am.module.storage.service.FileLinkTargetResolver;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

@RestController
public class FileLinkDownloadController {

    private final FileLinkService fileLinkService;
    private final List<FileLinkTargetResolver> targetResolvers;

    public FileLinkDownloadController(
            FileLinkService fileLinkService, List<FileLinkTargetResolver> targetResolvers) {
        this.fileLinkService = fileLinkService;
        this.targetResolvers = List.copyOf(targetResolvers);
    }

    @GetMapping("/api/v1/file-links/{code}:download")
    public ResponseEntity<InputStreamResource> downloadInternal(
            @PathVariable String code, Authentication authentication) {
        Long userId = currentUserId(authentication);
        FileLinkTarget target = fileLinkService.resolveInternal(code, userId);
        return toResponse(open(target, userId));
    }

    @GetMapping("/api/v1/public-file-links/{code}:download")
    public ResponseEntity<InputStreamResource> downloadPublic(@PathVariable String code) {
        FileLinkTarget target = fileLinkService.resolvePublic(code);
        return toResponse(open(target, null));
    }

    private StorageObjectDownload open(FileLinkTarget target, Long userId) {
        return targetResolvers.stream()
                .filter(resolver -> resolver.targetType() == target.targetType())
                .findFirst()
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND, "文件短链目标不存在"))
                .open(target, userId);
    }

    private ResponseEntity<InputStreamResource> toResponse(StorageObjectDownload download) {
        FileStorageResource resource = download.resource();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new InputStreamResource(download.resource().inputStream()));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
