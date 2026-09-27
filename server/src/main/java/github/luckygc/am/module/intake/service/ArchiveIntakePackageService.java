package github.luckygc.am.module.intake.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.io.LocalTemporaryFile;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.intake.ArchiveIntakePackage;
import github.luckygc.am.module.intake.ArchiveIntakePackageItem;
import github.luckygc.am.module.intake.ArchiveIntakePackageStatus;
import github.luckygc.am.module.intake.ArchiveIntakePackageValidation;
import github.luckygc.am.module.intake.ArchiveIntakeValidationCategory;
import github.luckygc.am.module.intake.ArchiveIntakeValidationOutcome;
import github.luckygc.am.module.intake._ArchiveIntakePackage;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageItemDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageValidationDataRepository;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifest;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.PackageStatistics;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageProcessingService.AcceptanceReview;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

@Service
public class ArchiveIntakePackageService {

    private static final String PERMISSION_CREATE = "archive:item:create";
    private static final String PERMISSION_READ = "archive:item:read";
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final ArchiveIntakePackageDataRepository packageRepository;
    private final ArchiveIntakePackageItemDataRepository packageItemRepository;
    private final ArchiveIntakePackageValidationDataRepository validationRepository;
    private final ArchiveIntakePackageRecordService recordService;
    private final ArchiveIntakePackageProcessingService processingService;
    private final ArchiveIntakePackageParser parser;
    private final StorageObjectService storageObjectService;
    private final FileLinkService fileLinkService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveIntakePackageService(
            ArchiveIntakePackageDataRepository packageRepository,
            ArchiveIntakePackageItemDataRepository packageItemRepository,
            ArchiveIntakePackageValidationDataRepository validationRepository,
            ArchiveIntakePackageRecordService recordService,
            ArchiveIntakePackageProcessingService processingService,
            ArchiveIntakePackageParser parser,
            StorageObjectService storageObjectService,
            FileLinkService fileLinkService,
            AuthorizationPermissionService permissionService) {
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.validationRepository = validationRepository;
        this.recordService = recordService;
        this.processingService = processingService;
        this.parser = parser;
        this.storageObjectService = storageObjectService;
        this.fileLinkService = fileLinkService;
        this.permissionService = permissionService;
    }

    public ArchiveIntakePackageDetailResponse receive(
            @Nullable String originalFileName,
            @Nullable String contentType,
            Path packagePath,
            long contentLength,
            Long userId) {
        userId = requirePermission(userId, PERMISSION_CREATE);
        String fileName = StringUtils.defaultIfBlank(originalFileName, "未命名信息包.zip");
        ArchiveIntakePackage received =
                recordService.createReceived(
                        fileName,
                        StringUtils.defaultIfBlank(contentType, "application/zip"),
                        contentLength,
                        packagePath,
                        userId);
        try {
            recordService.markChecking(received.getId());
            try (ArchiveIntakeManifest manifest = parser.parse(packagePath)) {
                PackageStatistics statistics =
                        Objects.requireNonNull(manifest.statistics(), "解析统计不能为空");
                int electronicFileCount =
                        manifest.items().stream()
                                .mapToInt(
                                        item ->
                                                item.metadataFiles().size()
                                                        + item.contentFiles().size())
                                .sum();
                long electronicFileBytes =
                        manifest.items().stream()
                                .flatMap(
                                        item ->
                                                java.util.stream.Stream.concat(
                                                        item.metadataFiles().stream(),
                                                        item.contentFiles().stream()))
                                .mapToLong(file -> file.size())
                                .sum();
                recordService.saveParsedResult(
                        received.getId(),
                        manifest.packageCode(),
                        statistics.itemCount(),
                        electronicFileCount,
                        electronicFileBytes,
                        manifest.validationResults());
            }
        } catch (RuntimeException exception) {
            recordService.markFailed(received.getId(), failureReason(exception));
        }
        return loadDetail(received.getId(), userId);
    }

    public ArchiveIntakePackageDetailResponse accept(
            Long id, @Nullable AcceptanceReview review, Long userId) {
        userId = requirePermission(userId, PERMISSION_CREATE);
        ArchiveIntakePackage intakePackage = loadOwned(id, userId);
        if (intakePackage.getStatus() != ArchiveIntakePackageStatus.PENDING_REVIEW) {
            throw new github.luckygc.am.common.exception.BadRequestException("只有待复核的信息包可以确认接收");
        }
        try (LocalTemporaryFile temporaryFile =
                LocalTemporaryFile.create("archive-intake-accept-", ".zip")) {
            StorageObjectDownload download =
                    openPackageObject(intakePackage.getOriginalStorageObjectId());
            try (FileStorageResource resource = download.resource()) {
                Files.copy(
                        resource.inputStream(),
                        temporaryFile.path(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (ArchiveIntakeManifest manifest = parser.parse(temporaryFile.path())) {
                processingService.accept(id, manifest, review, userId);
            }
        } catch (IOException exception) {
            recordService.markAcceptanceFailed(id, "原始信息包读取失败");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "原始信息包读取失败", exception);
        } catch (RuntimeException exception) {
            recordService.markAcceptanceFailed(id, failureReason(exception));
            throw exception;
        }
        return loadDetail(id, userId);
    }

    public ArchiveIntakePackageDetailResponse reject(Long id, String reason, Long userId) {
        userId = requirePermission(userId, PERMISSION_CREATE);
        loadOwned(id, userId);
        String normalizedReason = StringUtils.trimToNull(reason);
        if (normalizedReason == null) {
            throw new github.luckygc.am.common.exception.BadRequestException("退回原因不能为空");
        }
        recordService.reject(id, normalizedReason, userId);
        return loadDetail(id, userId);
    }

    public ArchiveIntakePackageDownloadLinkResponse createDownloadLink(Long id, Long userId) {
        userId = requirePermission(userId, PERMISSION_READ);
        ArchiveIntakePackage intakePackage = loadOwned(id, userId);
        FileLinkService.FileLinkCreated link =
                fileLinkService.createUserLink(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        intakePackage.getOriginalStorageObjectId(),
                        DOWNLOAD_TTL,
                        userId);
        return new ArchiveIntakePackageDownloadLinkResponse(
                "/api/v1/file-links/" + link.code() + ":download", link.expiresAt());
    }

    public CursorPageResponse<ArchiveIntakePackageListItemResponse> list(
            PageRequest pageRequest, Long userId) {
        userId = requirePermission(userId, PERMISSION_READ);
        CursoredPage<ArchiveIntakePackage> page =
                packageRepository.find(
                        _ArchiveIntakePackage.receivedBy.equalTo(userId),
                        pageRequest,
                        Order.by(
                                _ArchiveIntakePackage.createdAt.desc(),
                                _ArchiveIntakePackage.id.desc()));
        return CursorPageResponse.from(page, pageRequest, this::toListItem);
    }

    public ArchiveIntakePackageDetailResponse get(Long id, Long userId) {
        userId = requirePermission(userId, PERMISSION_READ);
        return loadDetail(id, userId);
    }

    private ArchiveIntakePackageDetailResponse loadDetail(Long id, Long userId) {
        ArchiveIntakePackage entity = loadOwned(id, userId);
        List<ArchiveIntakePackageGeneratedItemResponse> generatedItems =
                packageItemRepository.findByIntakePackageId(entity.getId()).stream()
                        .map(this::toGeneratedItem)
                        .toList();
        List<ArchiveIntakeValidationResponse> validations =
                validationRepository.findByIntakePackageId(entity.getId()).stream()
                        .map(this::toValidation)
                        .toList();
        return new ArchiveIntakePackageDetailResponse(
                entity.getId(),
                entity.getFormatProfile(),
                entity.getPackageCode(),
                entity.getOriginalFileName(),
                entity.getContentLength(),
                entity.getSha256(),
                entity.getStatus(),
                entity.getItemCount(),
                entity.getElectronicFileCount(),
                entity.getElectronicFileBytes(),
                entity.getValidationPassedCount(),
                entity.getValidationWarningCount(),
                entity.getValidationManualCount(),
                entity.getFailureReason(),
                entity.getReceivedBy(),
                entity.getReviewedBy(),
                entity.getReviewRemark(),
                entity.isSourceFixityConfirmed(),
                entity.isContentReadabilityConfirmed(),
                entity.isAntivirusPassed(),
                entity.isCarrierSafetyConfirmed(),
                entity.isHandoverCompleted(),
                entity.getProcessingStartedAt(),
                entity.getProcessingCompletedAt(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                validations,
                generatedItems);
    }

    private ArchiveIntakePackage loadOwned(Long id, Long userId) {
        return packageRepository
                .findById(id)
                .filter(row -> userId.equals(row.getReceivedBy()))
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案信息包接收记录不存在"));
    }

    private StorageObjectDownload openPackageObject(Long storageObjectId) {
        return storageObjectService.openObject(storageObjectId);
    }

    private ArchiveIntakePackageListItemResponse toListItem(ArchiveIntakePackage entity) {
        return new ArchiveIntakePackageListItemResponse(
                entity.getId(),
                entity.getFormatProfile(),
                entity.getPackageCode(),
                entity.getOriginalFileName(),
                entity.getContentLength(),
                entity.getStatus(),
                entity.getItemCount(),
                entity.getElectronicFileCount(),
                entity.getElectronicFileBytes(),
                entity.getValidationPassedCount(),
                entity.getValidationWarningCount(),
                entity.getValidationManualCount(),
                entity.getFailureReason(),
                entity.getProcessingCompletedAt(),
                entity.getReviewedAt(),
                entity.getCreatedAt());
    }

    private ArchiveIntakePackageGeneratedItemResponse toGeneratedItem(
            ArchiveIntakePackageItem entity) {
        return new ArchiveIntakePackageGeneratedItemResponse(
                entity.getArchiveItemId(),
                entity.getItemOrder(),
                entity.getFondsCode(),
                entity.getCategoryCode(),
                entity.getArchiveNo(),
                entity.getElectronicFileCount());
    }

    private ArchiveIntakeValidationResponse toValidation(ArchiveIntakePackageValidation entity) {
        return new ArchiveIntakeValidationResponse(
                entity.getValidationCode(),
                entity.getValidationCategory(),
                entity.getOutcome(),
                entity.getMessage());
    }

    private Long requirePermission(Long userId, String permission) {
        Long resolvedUserId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(resolvedUserId, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
        return resolvedUserId;
    }

    private String failureReason(RuntimeException exception) {
        String message = null;
        if (exception instanceof ResponseStatusException statusException) {
            message = statusException.getReason();
        } else if (exception instanceof github.luckygc.am.common.exception.BadRequestException) {
            message = exception.getMessage();
        }
        return StringUtils.defaultIfBlank(message, "信息包处理失败");
    }

    public record ArchiveIntakePackageListItemResponse(
            Long id,
            String formatProfile,
            @Nullable String packageCode,
            String originalFileName,
            long contentLength,
            ArchiveIntakePackageStatus status,
            int itemCount,
            int electronicFileCount,
            long electronicFileBytes,
            int validationPassedCount,
            int validationWarningCount,
            int validationManualCount,
            @Nullable String failureReason,
            @Nullable LocalDateTime processingCompletedAt,
            @Nullable LocalDateTime reviewedAt,
            LocalDateTime createdAt) {}

    public record ArchiveIntakePackageDetailResponse(
            Long id,
            String formatProfile,
            @Nullable String packageCode,
            String originalFileName,
            long contentLength,
            String sha256,
            ArchiveIntakePackageStatus status,
            int itemCount,
            int electronicFileCount,
            long electronicFileBytes,
            int validationPassedCount,
            int validationWarningCount,
            int validationManualCount,
            @Nullable String failureReason,
            Long receivedBy,
            @Nullable Long reviewedBy,
            @Nullable String reviewRemark,
            boolean sourceFixityConfirmed,
            boolean contentReadabilityConfirmed,
            boolean antivirusPassed,
            boolean carrierSafetyConfirmed,
            boolean handoverCompleted,
            @Nullable LocalDateTime processingStartedAt,
            @Nullable LocalDateTime processingCompletedAt,
            @Nullable LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            List<ArchiveIntakeValidationResponse> validations,
            List<ArchiveIntakePackageGeneratedItemResponse> generatedItems) {}

    public record ArchiveIntakePackageGeneratedItemResponse(
            Long archiveItemId,
            int itemOrder,
            String fondsCode,
            String categoryCode,
            @Nullable String archiveNo,
            int electronicFileCount) {}

    public record ArchiveIntakeValidationResponse(
            String code,
            ArchiveIntakeValidationCategory category,
            ArchiveIntakeValidationOutcome outcome,
            String message) {}

    public record ArchiveIntakePackageDownloadLinkResponse(String url, LocalDateTime expiresAt) {}
}
