package github.luckygc.am.module.intake.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.UploadArchiveItemElectronicFileRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemService;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.library.ArchiveRepository;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.intake.ArchiveIntakePackage;
import github.luckygc.am.module.intake.ArchiveIntakePackageItem;
import github.luckygc.am.module.intake.ArchiveIntakePackageStatus;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageItemDataRepository;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifest;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifestItem;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ParsedFile;

@Service
public class ArchiveIntakePackageProcessingService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ArchiveIntakePackageProcessingService.class);

    private final ArchiveIntakePackageDataRepository packageRepository;
    private final ArchiveIntakePackageItemDataRepository packageItemRepository;
    private final ArchiveRepositoryService archiveRepositoryService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMetadataReferenceService metadataReferenceService;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveItemService archiveItemService;
    private final ArchiveItemElectronicFileService electronicFileService;
    private final Clock clock;

    public ArchiveIntakePackageProcessingService(
            ArchiveIntakePackageDataRepository packageRepository,
            ArchiveIntakePackageItemDataRepository packageItemRepository,
            ArchiveRepositoryService archiveRepositoryService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveMetadataReferenceService metadataReferenceService,
            ArchiveMetadataService archiveMetadataService,
            ArchiveItemService archiveItemService,
            ArchiveItemElectronicFileService electronicFileService,
            Clock clock) {
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.archiveRepositoryService = archiveRepositoryService;
        this.archiveCategoryService = archiveCategoryService;
        this.metadataReferenceService = metadataReferenceService;
        this.archiveMetadataService = archiveMetadataService;
        this.archiveItemService = archiveItemService;
        this.electronicFileService = electronicFileService;
        this.clock = clock;
    }

    @Transactional
    public void accept(
            Long packageId, ArchiveIntakeManifest manifest, AcceptanceReview review, Long userId) {
        ArchiveIntakePackage intakePackage =
                packageRepository
                        .findById(packageId)
                        .orElseThrow(() -> new BadRequestException("档案信息包接收记录不存在"));
        if (intakePackage.getStatus() != ArchiveIntakePackageStatus.PENDING_REVIEW) {
            throw new BadRequestException("只有待复核的信息包可以确认接收");
        }
        requireCompleteReview(review);
        intakePackage.setStatus(ArchiveIntakePackageStatus.ACCEPTING);
        intakePackage.setFailureReason(null);
        applyReview(intakePackage, review, userId);
        packageRepository.update(intakePackage);

        ArchiveRepository holdingRepository =
                archiveRepositoryService.getEnabledSystemRepository(ArchiveRepositoryRole.HOLDING);
        List<ArchiveIntakeManifestItem> items = manifest.items();
        for (int index = 0; index < items.size(); index++) {
            createItem(packageId, index, items.get(index), holdingRepository.getId(), userId);
        }

        intakePackage.setStatus(ArchiveIntakePackageStatus.ACCEPTED);
        intakePackage.setItemCount(items.size());
        intakePackage.setFailureReason(null);
        intakePackage.setReviewedAt(LocalDateTime.now(clock));
        packageRepository.update(intakePackage);
    }

    private void createItem(
            Long packageId,
            int index,
            ArchiveIntakeManifestItem item,
            Long holdingRepositoryId,
            Long userId) {
        try {
            ArchiveCategoryDto category =
                    archiveCategoryService.getEnabledCategoryByCode(item.categoryCode());
            Long securityLevelId =
                    metadataReferenceService
                            .getEnabledSecurityLevelByName(item.securityLevel())
                            .id();
            Long retentionPeriodId =
                    metadataReferenceService
                            .getEnabledRetentionPeriodByName(item.retentionPeriod())
                            .id();
            ArchiveItemDto archiveItem =
                    archiveItemService.createItem(
                            new CreateArchiveItemRequest(
                                    category.id(),
                                    null,
                                    StringUtils.trim(item.fondsCode()),
                                    StringUtils.trimToNull(item.archiveNo()),
                                    item.archiveYear(),
                                    securityLevelId,
                                    retentionPeriodId,
                                    null,
                                    dynamicFields(category.id(), item),
                                    holdingRepositoryId),
                            userId);
            int fileCount = uploadFiles(archiveItem.id(), item, userId);
            ArchiveIntakePackageItem relation = new ArchiveIntakePackageItem();
            relation.setIntakePackageId(packageId);
            relation.setArchiveItemId(archiveItem.id());
            relation.setItemOrder(index);
            relation.setFondsCode(archiveItem.fondsCode());
            relation.setCategoryCode(archiveItem.categoryCode());
            relation.setArchiveNo(archiveItem.archiveNo());
            relation.setElectronicFileCount(fileCount);
            packageItemRepository.insert(relation);
        } catch (RuntimeException exception) {
            if (!(exception instanceof ResponseStatusException)
                    && !(exception instanceof BadRequestException)) {
                LOGGER.error(
                        "电子档案移交信息包入库异常: packageId={}, itemOrder={}", packageId, index, exception);
            }
            throw new BadRequestException(
                    "第 " + (index + 1) + " 份电子档案入库失败：" + businessMessage(exception));
        }
    }

    private int uploadFiles(Long archiveItemId, ArchiveIntakeManifestItem item, Long userId) {
        int displayOrder = 0;
        for (ParsedFile file : item.metadataFiles()) {
            uploadFile(archiveItemId, file, "METADATA", displayOrder++, userId);
        }
        for (ParsedFile file : item.contentFiles()) {
            uploadFile(archiveItemId, file, "ORIGINAL", displayOrder++, userId);
        }
        return displayOrder;
    }

    private void uploadFile(
            Long archiveItemId, ParsedFile file, String usageType, int displayOrder, Long userId) {
        try (InputStream inputStream = Files.newInputStream(file.temporaryPath())) {
            electronicFileService.uploadFile(
                    archiveItemId,
                    new UploadArchiveItemElectronicFileRequest(
                            file.originalName(),
                            contentType(file),
                            file.size(),
                            inputStream,
                            usageType,
                            displayOrder),
                    userId);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "电子档案文件读取失败", exception);
        }
    }

    private @Nullable String contentType(ParsedFile file) {
        if (file.originalName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            return "application/xml";
        }
        try {
            return Files.probeContentType(file.temporaryPath());
        } catch (IOException exception) {
            return null;
        }
    }

    private Map<String, @Nullable Object> dynamicFields(
            Long categoryId, ArchiveIntakeManifestItem item) {
        Map<String, @Nullable Object> standardValues = new LinkedHashMap<>();
        standardValues.put("顺序号", item.sequenceNumber());
        standardValues.put("责任者", item.responsible());
        standardValues.put("题名", item.title());
        standardValues.put("日期", item.date());
        standardValues.put("页数", item.pageCount());
        standardValues.put("备注", item.remarks());
        Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        for (ArchiveFieldDto field :
                archiveMetadataService.listEnabledFields(categoryId, ArchiveLevel.ITEM)) {
            if (standardValues.containsKey(field.fieldName())) {
                fields.put(field.fieldCode(), standardValues.get(field.fieldName()));
            }
        }
        return fields;
    }

    private void requireCompleteReview(AcceptanceReview review) {
        if (review == null
                || !review.sourceFixityConfirmed()
                || !review.contentReadabilityConfirmed()
                || !review.antivirusPassed()
                || !review.carrierSafetyConfirmed()
                || !review.handoverCompleted()) {
            throw new BadRequestException("确认接收前必须完成全部人工检测和交接确认");
        }
    }

    private void applyReview(
            ArchiveIntakePackage intakePackage, AcceptanceReview review, Long userId) {
        intakePackage.setReviewedBy(userId);
        intakePackage.setReviewRemark(StringUtils.trimToNull(review.remark()));
        intakePackage.setSourceFixityConfirmed(review.sourceFixityConfirmed());
        intakePackage.setContentReadabilityConfirmed(review.contentReadabilityConfirmed());
        intakePackage.setAntivirusPassed(review.antivirusPassed());
        intakePackage.setCarrierSafetyConfirmed(review.carrierSafetyConfirmed());
        intakePackage.setHandoverCompleted(review.handoverCompleted());
    }

    private String businessMessage(RuntimeException exception) {
        if (exception instanceof ResponseStatusException status
                && StringUtils.isNotBlank(status.getReason())) {
            return status.getReason();
        }
        if (exception instanceof BadRequestException) {
            return StringUtils.defaultIfBlank(exception.getMessage(), "电子档案校验失败");
        }
        return "电子档案校验失败";
    }

    public record AcceptanceReview(
            boolean sourceFixityConfirmed,
            boolean contentReadabilityConfirmed,
            boolean antivirusPassed,
            boolean carrierSafetyConfirmed,
            boolean handoverCompleted,
            @Nullable String remark) {}
}
