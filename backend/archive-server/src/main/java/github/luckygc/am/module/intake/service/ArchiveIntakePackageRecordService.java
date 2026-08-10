package github.luckygc.am.module.intake.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.intake.ArchiveIntakePackage;
import github.luckygc.am.module.intake.ArchiveIntakePackageStatus;
import github.luckygc.am.module.intake.ArchiveIntakePackageValidation;
import github.luckygc.am.module.intake.ArchiveIntakeValidationOutcome;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageValidationDataRepository;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeValidationResult;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;

@Service
public class ArchiveIntakePackageRecordService {

    public static final String FORMAT_PROFILE = "DAT93_ITEM";

    private final ArchiveIntakePackageDataRepository repository;
    private final ArchiveIntakePackageValidationDataRepository validationRepository;
    private final StorageObjectService storageObjectService;
    private final Clock clock;

    public ArchiveIntakePackageRecordService(
            ArchiveIntakePackageDataRepository repository,
            ArchiveIntakePackageValidationDataRepository validationRepository,
            StorageObjectService storageObjectService,
            Clock clock) {
        this.repository = repository;
        this.validationRepository = validationRepository;
        this.storageObjectService = storageObjectService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArchiveIntakePackage createReceived(
            String originalFileName,
            String contentType,
            long contentLength,
            Path packagePath,
            Long receivedBy) {
        StorageObjectDto storageObject;
        try (InputStream inputStream = Files.newInputStream(packagePath)) {
            storageObject =
                    storageObjectService.storeObject(
                            new StorageObjectService.StoreStorageObjectRequest(
                                    originalFileName,
                                    contentType,
                                    contentLength,
                                    inputStream,
                                    null),
                            receivedBy);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "原始信息包保存失败", exception);
        }
        ArchiveIntakePackage entity = new ArchiveIntakePackage();
        entity.setFormatProfile(FORMAT_PROFILE);
        entity.setOriginalFileName(normalizeFileName(originalFileName));
        entity.setContentLength(contentLength);
        entity.setSha256(
                Objects.requireNonNull(
                        StringUtils.trimToNull(storageObject.checksumSha256()),
                        "原始信息包 SHA-256 摘要不能为空"));
        entity.setOriginalStorageObjectId(storageObject.id());
        entity.setStatus(ArchiveIntakePackageStatus.RECEIVED);
        entity.setReceivedBy(receivedBy);
        return repository.insert(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChecking(Long id) {
        ArchiveIntakePackage entity = load(id);
        if (entity.getStatus() != ArchiveIntakePackageStatus.RECEIVED) {
            throw new BadRequestException("档案信息包状态不允许检测");
        }
        entity.setStatus(ArchiveIntakePackageStatus.CHECKING);
        entity.setProcessingStartedAt(LocalDateTime.now(clock));
        entity.setFailureReason(null);
        repository.update(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveParsedResult(
            Long id,
            @org.jspecify.annotations.Nullable String packageCode,
            int itemCount,
            int electronicFileCount,
            long electronicFileBytes,
            List<ArchiveIntakeValidationResult> validationResults) {
        ArchiveIntakePackage entity = load(id);
        if (entity.getStatus() != ArchiveIntakePackageStatus.CHECKING) {
            throw new BadRequestException("档案信息包状态不允许保存检测结果");
        }
        entity.setPackageCode(StringUtils.trimToNull(packageCode));
        entity.setItemCount(itemCount);
        entity.setElectronicFileCount(electronicFileCount);
        entity.setElectronicFileBytes(electronicFileBytes);
        entity.setValidationPassedCount(
                count(validationResults, ArchiveIntakeValidationOutcome.PASSED));
        entity.setValidationWarningCount(
                count(validationResults, ArchiveIntakeValidationOutcome.WARNING));
        entity.setValidationManualCount(
                count(validationResults, ArchiveIntakeValidationOutcome.MANUAL_REVIEW));
        entity.setStatus(ArchiveIntakePackageStatus.PENDING_REVIEW);
        entity.setProcessingCompletedAt(LocalDateTime.now(clock));
        entity.setFailureReason(null);
        repository.update(entity);
        if (!validationResults.isEmpty()) {
            validationRepository.insertAll(
                    validationResults.stream().map(result -> validation(id, result)).toList());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String failureReason) {
        ArchiveIntakePackage entity = load(id);
        entity.setStatus(ArchiveIntakePackageStatus.FAILED);
        entity.setFailureReason(limitReason(failureReason));
        entity.setProcessingCompletedAt(LocalDateTime.now(clock));
        repository.update(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(Long id, String reason, Long userId) {
        ArchiveIntakePackage entity = load(id);
        if (entity.getStatus() != ArchiveIntakePackageStatus.PENDING_REVIEW) {
            throw new BadRequestException("只有待复核的信息包可以退回");
        }
        entity.setStatus(ArchiveIntakePackageStatus.REJECTED);
        entity.setFailureReason(limitReason(reason));
        entity.setReviewedBy(userId);
        entity.setReviewedAt(LocalDateTime.now(clock));
        repository.update(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAcceptanceFailed(Long id, String failureReason) {
        ArchiveIntakePackage entity = load(id);
        if (entity.getStatus() != ArchiveIntakePackageStatus.PENDING_REVIEW) {
            throw new BadRequestException("档案信息包接收入库状态异常");
        }
        entity.setFailureReason(limitReason(failureReason));
        repository.update(entity);
    }

    ArchiveIntakePackage load(Long id) {
        return repository.findById(id).orElseThrow(() -> new BadRequestException("档案信息包接收记录不存在"));
    }

    private int count(
            List<ArchiveIntakeValidationResult> results, ArchiveIntakeValidationOutcome outcome) {
        return Math.toIntExact(
                results.stream().filter(result -> result.outcome() == outcome).count());
    }

    private ArchiveIntakePackageValidation validation(
            Long packageId, ArchiveIntakeValidationResult result) {
        ArchiveIntakePackageValidation entity = new ArchiveIntakePackageValidation();
        entity.setIntakePackageId(packageId);
        entity.setValidationCode(result.code());
        entity.setValidationCategory(result.category());
        entity.setOutcome(result.outcome());
        entity.setMessage(limitValidationMessage(result.message()));
        return entity;
    }

    private String normalizeFileName(String originalFileName) {
        String normalized = StringUtils.trimToNull(originalFileName);
        if (normalized == null) {
            return "未命名信息包.zip";
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private String limitReason(String reason) {
        String normalized = StringUtils.defaultIfBlank(reason, "信息包处理失败");
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private String limitValidationMessage(String message) {
        String normalized = StringUtils.defaultIfBlank(message, "未提供检测说明");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
