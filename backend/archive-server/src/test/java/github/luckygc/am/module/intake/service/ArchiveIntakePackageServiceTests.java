package github.luckygc.am.module.intake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
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
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveRetentionPeriodDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveSecurityLevelDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.intake.ArchiveIntakePackage;
import github.luckygc.am.module.intake.ArchiveIntakePackageStatus;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageItemDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageValidationDataRepository;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifest;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifestItem;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.PackageStatistics;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageProcessingService.AcceptanceReview;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;

@DisplayName("电子档案移交信息包接收服务")
class ArchiveIntakePackageServiceTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T03:00:00Z"), ZoneOffset.UTC);

    private ArchiveIntakePackageDataRepository packageRepository;
    private ArchiveIntakePackageItemDataRepository packageItemRepository;
    private ArchiveIntakePackageValidationDataRepository validationRepository;
    private ArchiveIntakePackageRecordService recordService;
    private ArchiveIntakePackageProcessingService processingService;
    private ArchiveIntakePackageParser parser;
    private StorageObjectService storageObjectService;
    private FileLinkService fileLinkService;
    private AuthorizationPermissionService permissionService;

    @BeforeEach
    void setUp() {
        packageRepository = mock(ArchiveIntakePackageDataRepository.class);
        packageItemRepository = mock(ArchiveIntakePackageItemDataRepository.class);
        validationRepository = mock(ArchiveIntakePackageValidationDataRepository.class);
        recordService = mock(ArchiveIntakePackageRecordService.class);
        processingService = mock(ArchiveIntakePackageProcessingService.class);
        parser = mock(ArchiveIntakePackageParser.class);
        storageObjectService = mock(StorageObjectService.class);
        fileLinkService = mock(FileLinkService.class);
        permissionService = mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(true);
    }

    @Test
    @DisplayName("上传只完成自动检测并进入待复核，不提前生成档案")
    void receiveShouldStopAtPendingReview() {
        Path packagePath = Path.of("/tmp/package.zip");
        ArchiveIntakePackage received = intakePackage(10L, ArchiveIntakePackageStatus.RECEIVED);
        ArchiveIntakePackage pending =
                intakePackage(10L, ArchiveIntakePackageStatus.PENDING_REVIEW);
        ArchiveIntakeManifest manifest = mock(ArchiveIntakeManifest.class);
        when(manifest.items()).thenReturn(List.of());
        when(manifest.statistics()).thenReturn(new PackageStatistics(1, 2, 30L, 6));
        when(manifest.validationResults()).thenReturn(List.of());
        when(recordService.createReceived("package.zip", "application/zip", 3L, packagePath, 9L))
                .thenReturn(received);
        when(parser.parse(packagePath)).thenReturn(manifest);
        when(packageRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(packageItemRepository.findByIntakePackageId(10L)).thenReturn(List.of());
        when(validationRepository.findByIntakePackageId(10L)).thenReturn(List.of());

        var response = service().receive("package.zip", "application/zip", packagePath, 3L, 9L);

        assertThat(response.status()).isEqualTo(ArchiveIntakePackageStatus.PENDING_REVIEW);
        verify(recordService).markChecking(10L);
        verify(recordService).saveParsedResult(10L, null, 1, 0, 0L, List.of());
        verify(processingService, never()).accept(any(), any(), any(), any());
    }

    @Test
    @DisplayName("确认接收后档案直接以已归档状态进入正式库")
    void processorShouldAcceptIntoHoldingRepository() {
        ArchiveRepositoryService repositoryService = mock(ArchiveRepositoryService.class);
        ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
        ArchiveMetadataReferenceService referenceService =
                mock(ArchiveMetadataReferenceService.class);
        ArchiveMetadataService metadataService = mock(ArchiveMetadataService.class);
        ArchiveItemService itemService = mock(ArchiveItemService.class);
        ArchiveItemElectronicFileService electronicFileService =
                mock(ArchiveItemElectronicFileService.class);
        ArchiveIntakePackage pending =
                intakePackage(10L, ArchiveIntakePackageStatus.PENDING_REVIEW);
        when(packageRepository.findById(10L)).thenReturn(Optional.of(pending));
        ArchiveRepository holding = new ArchiveRepository();
        holding.setId(2L);
        holding.setRepositoryRole(ArchiveRepositoryRole.HOLDING);
        when(repositoryService.getEnabledSystemRepository(ArchiveRepositoryRole.HOLDING))
                .thenReturn(holding);
        ArchiveCategoryDto category = mock(ArchiveCategoryDto.class);
        when(category.id()).thenReturn(7L);
        when(categoryService.getEnabledCategoryByCode("WS")).thenReturn(category);
        ArchiveSecurityLevelDto security = mock(ArchiveSecurityLevelDto.class);
        when(security.id()).thenReturn(3L);
        when(referenceService.getEnabledSecurityLevelByName("公开")).thenReturn(security);
        ArchiveRetentionPeriodDto retention = mock(ArchiveRetentionPeriodDto.class);
        when(retention.id()).thenReturn(4L);
        when(referenceService.getEnabledRetentionPeriodByName("永久")).thenReturn(retention);
        when(metadataService.listEnabledFields(
                        7L, github.luckygc.am.module.archive.ArchiveLevel.ITEM))
                .thenReturn(List.of());
        when(itemService.createItem(any(), eq(9L))).thenReturn(item(101L, "WS-2026-001"));
        ArchiveIntakeManifestItem manifestItem = mock(ArchiveIntakeManifestItem.class);
        when(manifestItem.fondsCode()).thenReturn("F001");
        when(manifestItem.categoryCode()).thenReturn("WS");
        when(manifestItem.archiveNo()).thenReturn("WS-2026-001");
        when(manifestItem.archiveYear()).thenReturn(2026);
        when(manifestItem.securityLevel()).thenReturn("公开");
        when(manifestItem.retentionPeriod()).thenReturn("永久");
        when(manifestItem.metadataFiles()).thenReturn(List.of());
        when(manifestItem.contentFiles()).thenReturn(List.of());
        ArchiveIntakeManifest manifest = mock(ArchiveIntakeManifest.class);
        when(manifest.items()).thenReturn(List.of(manifestItem));
        ArchiveIntakePackageProcessingService processor =
                new ArchiveIntakePackageProcessingService(
                        packageRepository,
                        packageItemRepository,
                        repositoryService,
                        categoryService,
                        referenceService,
                        metadataService,
                        itemService,
                        electronicFileService,
                        CLOCK);

        processor.accept(10L, manifest, acceptedReview(), 9L);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(CreateArchiveItemRequest.class);
        verify(itemService).createItem(requestCaptor.capture(), eq(9L));
        assertThat(requestCaptor.getValue().repositoryId()).isEqualTo(2L);
        assertThat(pending.getStatus()).isEqualTo(ArchiveIntakePackageStatus.ACCEPTED);
        assertThat(pending.isHandoverCompleted()).isTrue();
        verify(packageItemRepository).insert(any());
    }

    @Test
    @DisplayName("人工检测或交接未全部确认时禁止接收")
    void processorShouldRequireAllManualReviewItems() {
        ArchiveIntakePackage pending =
                intakePackage(10L, ArchiveIntakePackageStatus.PENDING_REVIEW);
        when(packageRepository.findById(10L)).thenReturn(Optional.of(pending));
        ArchiveIntakePackageProcessingService processor =
                new ArchiveIntakePackageProcessingService(
                        packageRepository,
                        packageItemRepository,
                        mock(ArchiveRepositoryService.class),
                        mock(ArchiveCategoryService.class),
                        mock(ArchiveMetadataReferenceService.class),
                        mock(ArchiveMetadataService.class),
                        mock(ArchiveItemService.class),
                        mock(ArchiveItemElectronicFileService.class),
                        CLOCK);

        assertThatThrownBy(
                        () ->
                                processor.accept(
                                        10L,
                                        mock(ArchiveIntakeManifest.class),
                                        new AcceptanceReview(true, true, false, true, true, null),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("全部人工检测和交接确认");
        assertThat(pending.getStatus()).isEqualTo(ArchiveIntakePackageStatus.PENDING_REVIEW);
    }

    private ArchiveIntakePackageService service() {
        return new ArchiveIntakePackageService(
                packageRepository,
                packageItemRepository,
                validationRepository,
                recordService,
                processingService,
                parser,
                storageObjectService,
                fileLinkService,
                permissionService);
    }

    private AcceptanceReview acceptedReview() {
        return new AcceptanceReview(true, true, true, true, true, "线下交接手续已完成");
    }

    private ArchiveItemDto item(Long id, String archiveNo) {
        return new ArchiveItemDto(
                id, null, "F001", "测试全宗", "WS", "文书", archiveNo, 3L, 4L, 2026, false, null, null,
                null, 2L);
    }

    private ArchiveIntakePackage intakePackage(Long id, ArchiveIntakePackageStatus status) {
        ArchiveIntakePackage entity = new ArchiveIntakePackage();
        entity.setId(id);
        entity.setFormatProfile("DAT93_ITEM");
        entity.setOriginalFileName("package.zip");
        entity.setContentLength(3);
        entity.setSha256("0".repeat(64));
        entity.setOriginalStorageObjectId(8L);
        entity.setStatus(status);
        entity.setReceivedBy(9L);
        entity.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 29, 11, 0));
        return entity;
    }
}
