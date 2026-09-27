package github.luckygc.am.module.intake.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import jakarta.data.page.PageRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.io.LocalTemporaryFile;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageProcessingService.AcceptanceReview;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageService;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageService.ArchiveIntakePackageDetailResponse;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageService.ArchiveIntakePackageDownloadLinkResponse;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageService.ArchiveIntakePackageListItemResponse;

@RestController
public class ArchiveIntakePackageController {

    private final ArchiveIntakePackageService service;

    public ArchiveIntakePackageController(ArchiveIntakePackageService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/api/v1/archive-intake-packages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveIntakePackageDetailResponse create(
            @RequestPart("file") MultipartFile file, Authentication authentication) {
        if (file.getSize() > ArchiveIntakePackageParser.MAX_COMPRESSED_BYTES) {
            throw new BadRequestException("信息包不能超过 50 MiB");
        }
        try (LocalTemporaryFile temporaryFile =
                        LocalTemporaryFile.create("archive-intake-", ".zip");
                InputStream inputStream = file.getInputStream()) {
            Files.copy(
                    inputStream,
                    temporaryFile.path(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return service.receive(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    temporaryFile.path(),
                    file.getSize(),
                    AuthenticatedUsers.requireUserId(
                            authentication == null ? null : authentication.getPrincipal()));
        } catch (IOException exception) {
            throw new BadRequestException("信息包读取失败");
        }
    }

    @GetMapping("/api/v1/archive-intake-packages")
    public CursorPageResponse<ArchiveIntakePackageListItemResponse> list(
            PageRequest pageRequest, Authentication authentication) {
        return service.list(
                pageRequest,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/archive-intake-packages/{id}")
    public ArchiveIntakePackageDetailResponse get(
            @PathVariable Long id, Authentication authentication) {
        return service.get(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-intake-packages/{id}:accept")
    public ArchiveIntakePackageDetailResponse accept(
            @PathVariable Long id,
            @RequestBody AcceptanceReview review,
            Authentication authentication) {
        return service.accept(
                id,
                review,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-intake-packages/{id}:reject")
    public ArchiveIntakePackageDetailResponse reject(
            @PathVariable Long id,
            @RequestBody RejectArchiveIntakePackageRequest request,
            Authentication authentication) {
        return service.reject(
                id,
                request.reason(),
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    @PostMapping("/api/v1/archive-intake-packages/{id}:createDownloadLink")
    public ArchiveIntakePackageDownloadLinkResponse createDownloadLink(
            @PathVariable Long id, Authentication authentication) {
        return service.createDownloadLink(
                id,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }

    public record RejectArchiveIntakePackageRequest(String reason) {}
}
