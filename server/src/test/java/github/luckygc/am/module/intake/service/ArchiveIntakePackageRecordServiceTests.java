package github.luckygc.am.module.intake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.module.intake.ArchiveIntakePackage;
import github.luckygc.am.module.intake.ArchiveIntakePackageStatus;
import github.luckygc.am.module.intake.ArchiveIntakePackageValidation;
import github.luckygc.am.module.intake.ArchiveIntakeValidationCategory;
import github.luckygc.am.module.intake.ArchiveIntakeValidationOutcome;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageDataRepository;
import github.luckygc.am.module.intake.repository.ArchiveIntakePackageValidationDataRepository;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeValidationResult;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;
import github.luckygc.am.module.storage.service.StorageObjectService.StoreStorageObjectRequest;

@DisplayName("档案信息包接收记录")
class ArchiveIntakePackageRecordServiceTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T03:00:00Z"), ZoneOffset.UTC);

    @TempDir Path tempDirectory;

    @Test
    @DisplayName("先长期保存原始包并以存储摘要创建接收记录")
    void createReceivedShouldKeepOriginalPackageAndDigest() throws Exception {
        ArchiveIntakePackageDataRepository repository =
                mock(ArchiveIntakePackageDataRepository.class);
        ArchiveIntakePackageValidationDataRepository validationRepository =
                mock(ArchiveIntakePackageValidationDataRepository.class);
        StorageObjectService storageObjectService = mock(StorageObjectService.class);
        Path packagePath = tempDirectory.resolve("package.zip");
        Files.writeString(packagePath, "zip-content");
        String digest = "a".repeat(64);
        when(storageObjectService.storeObject(any(), eq(9L)))
                .thenAnswer(
                        invocation -> {
                            StoreStorageObjectRequest request = invocation.getArgument(0);
                            assertThat(request.inputStream().readAllBytes())
                                    .isEqualTo("zip-content".getBytes());
                            return new StorageObjectDto(
                                    8L,
                                    "archive",
                                    "intake/package.zip",
                                    "package.zip",
                                    11,
                                    "application/zip",
                                    digest,
                                    9L);
                        });
        when(repository.insert(any()))
                .thenAnswer(
                        invocation -> {
                            ArchiveIntakePackage entity = invocation.getArgument(0);
                            entity.setId(10L);
                            return entity;
                        });

        ArchiveIntakePackage created =
                service(repository, validationRepository, storageObjectService)
                        .createReceived("package.zip", "application/zip", 11, packagePath, 9L);

        assertThat(created.getStatus()).isEqualTo(ArchiveIntakePackageStatus.RECEIVED);
        assertThat(created.getFormatProfile()).isEqualTo("DAT93_ITEM");
        assertThat(created.getOriginalStorageObjectId()).isEqualTo(8L);
        assertThat(created.getSha256()).isEqualTo(digest);
    }

    @Test
    @DisplayName("自动检测摘要和逐项结果在同一记录事务保存")
    void saveParsedResultShouldPersistSummaryAndValidationDetails() {
        ArchiveIntakePackageDataRepository repository =
                mock(ArchiveIntakePackageDataRepository.class);
        ArchiveIntakePackageValidationDataRepository validationRepository =
                mock(ArchiveIntakePackageValidationDataRepository.class);
        ArchiveIntakePackage entity = new ArchiveIntakePackage();
        entity.setId(10L);
        entity.setStatus(ArchiveIntakePackageStatus.CHECKING);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));
        List<ArchiveIntakeValidationResult> results =
                List.of(
                        result(
                                "STRUCTURE",
                                ArchiveIntakeValidationCategory.INTEGRITY,
                                ArchiveIntakeValidationOutcome.PASSED),
                        result(
                                "PAGE_COUNT",
                                ArchiveIntakeValidationCategory.INTEGRITY,
                                ArchiveIntakeValidationOutcome.WARNING),
                        result(
                                "ANTIVIRUS",
                                ArchiveIntakeValidationCategory.SECURITY,
                                ArchiveIntakeValidationOutcome.MANUAL_REVIEW));

        service(repository, validationRepository, mock(StorageObjectService.class))
                .saveParsedResult(10L, "MEDIA-001", 2, 5, 1024, results);

        assertThat(entity.getStatus()).isEqualTo(ArchiveIntakePackageStatus.PENDING_REVIEW);
        assertThat(entity.getPackageCode()).isEqualTo("MEDIA-001");
        assertThat(entity.getItemCount()).isEqualTo(2);
        assertThat(entity.getElectronicFileCount()).isEqualTo(5);
        assertThat(entity.getElectronicFileBytes()).isEqualTo(1024);
        assertThat(entity.getValidationPassedCount()).isEqualTo(1);
        assertThat(entity.getValidationWarningCount()).isEqualTo(1);
        assertThat(entity.getValidationManualCount()).isEqualTo(1);
        assertThat(entity.getProcessingCompletedAt())
                .isEqualTo(java.time.LocalDateTime.of(2026, 7, 29, 3, 0));
        verify(repository).update(entity);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArchiveIntakePackageValidation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(validationRepository).insertAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(ArchiveIntakePackageValidation::getValidationCode)
                .containsExactly("STRUCTURE", "PAGE_COUNT", "ANTIVIRUS");
    }

    private ArchiveIntakeValidationResult result(
            String code,
            ArchiveIntakeValidationCategory category,
            ArchiveIntakeValidationOutcome outcome) {
        return new ArchiveIntakeValidationResult(code, category, outcome, code);
    }

    private ArchiveIntakePackageRecordService service(
            ArchiveIntakePackageDataRepository repository,
            ArchiveIntakePackageValidationDataRepository validationRepository,
            StorageObjectService storageObjectService) {
        return new ArchiveIntakePackageRecordService(
                repository, validationRepository, storageObjectService, CLOCK);
    }
}
