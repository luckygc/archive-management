package github.luckygc.am.module.intake.service;

import static github.luckygc.am.test.ArchiveTestFixtures.insertActiveFonds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemService;
import github.luckygc.am.module.archive.library.ArchiveRepository;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveRetentionPeriodDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveSecurityLevelDto;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifest;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser.ArchiveIntakeManifestItem;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageProcessingService.AcceptanceReview;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("档案信息包全包事务")
class ArchiveIntakePackageTransactionIntegrationTests extends PostgreSqlContainerTest {

    private static final String ARCHIVE_NO_PREFIX = "INTAKE-TX-";

    @Autowired private ArchiveIntakePackageProcessingService processingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArchiveRepositoryService archiveRepositoryService;
    @MockitoBean private ArchiveCategoryService archiveCategoryService;
    @MockitoBean private ArchiveMetadataReferenceService metadataReferenceService;
    @MockitoBean private ArchiveMetadataService archiveMetadataService;
    @MockitoBean private ArchiveItemService archiveItemCommandService;
    @MockitoBean private ArchiveItemElectronicFileService electronicFileService;

    @AfterEach
    void cleanData() {
        jdbcTemplate.update(
                "delete from am_archive_item where archive_no like ?", ARCHIVE_NO_PREFIX + "%");
        jdbcTemplate.update(
                "delete from am_archive_intake_package where original_file_name = ?",
                "transaction-test.zip");
        jdbcTemplate.update(
                "delete from am_storage_object where object_key = ?",
                "intake/transaction-test.zip");
    }

    @Test
    @DisplayName("第二个条目失败时回滚已写入的档案条目和包关联")
    void processShouldRollbackEveryItemAndRelation() {
        insertActiveFonds(jdbcTemplate, "F001", "测试全宗");
        Long storageObjectId =
                jdbcTemplate.queryForObject(
                        """
                        insert into am_storage_object
                            (bucket_name, object_key, original_filename, file_size, created_by)
                        values ('test', 'intake/transaction-test.zip', 'transaction-test.zip', 1, 9)
                        returning id
                        """,
                        Long.class);
        Long packageId =
                jdbcTemplate.queryForObject(
                        """
                        insert into am_archive_intake_package
                            (format_profile, original_file_name, content_length, sha256,
                             original_storage_object_id, status, received_by)
                        values ('DAT93_ITEM', ?, 1, ?, ?, 'PENDING_REVIEW', 9)
                        returning id
                        """,
                        Long.class,
                        "transaction-test.zip",
                        "0".repeat(64),
                        storageObjectId);
        ArchiveIntakeManifest manifest = mock(ArchiveIntakeManifest.class);
        List<ArchiveIntakeManifestItem> manifestItems =
                List.of(manifestItem("1"), manifestItem("2"));
        when(manifest.items()).thenReturn(manifestItems);
        ArchiveRepository holdingRepository = new ArchiveRepository();
        holdingRepository.setId(1L);
        when(archiveRepositoryService.getEnabledSystemRepository(ArchiveRepositoryRole.HOLDING))
                .thenReturn(holdingRepository);
        ArchiveCategoryDto category = mock(ArchiveCategoryDto.class);
        when(category.id()).thenReturn(7L);
        when(archiveCategoryService.getEnabledCategoryByCode("WS")).thenReturn(category);
        ArchiveSecurityLevelDto securityLevel = mock(ArchiveSecurityLevelDto.class);
        when(securityLevel.id()).thenReturn(3L);
        when(metadataReferenceService.getEnabledSecurityLevelByName("公开"))
                .thenReturn(securityLevel);
        ArchiveRetentionPeriodDto retentionPeriod = mock(ArchiveRetentionPeriodDto.class);
        when(retentionPeriod.id()).thenReturn(4L);
        when(metadataReferenceService.getEnabledRetentionPeriodByName("永久"))
                .thenReturn(retentionPeriod);
        when(archiveMetadataService.listEnabledFields(
                        7L, github.luckygc.am.module.archive.ArchiveLevel.ITEM))
                .thenReturn(List.of());
        AtomicInteger sequence = new AtomicInteger();
        when(archiveItemCommandService.createItem(any(), eq(9L)))
                .thenAnswer(
                        invocation -> {
                            int current = sequence.incrementAndGet();
                            String archiveNo = ARCHIVE_NO_PREFIX + current;
                            Long itemId =
                                    jdbcTemplate.queryForObject(
                                            """
                                            insert into am_archive_item
                                                (fonds_code, fonds_name, category_code, category_name,
                                                 archive_no, archive_year, repository_id)
                                            values ('F001', '测试全宗', 'WS', '文书', ?, 2026, 1)
                                            returning id
                                            """,
                                            Long.class,
                                            archiveNo);
                            if (current == 2) {
                                throw new BadRequestException("模拟第二条校验失败");
                            }
                            return item(itemId, archiveNo);
                        });

        assertThatThrownBy(
                        () ->
                                processingService.accept(
                                        packageId,
                                        manifest,
                                        new AcceptanceReview(true, true, true, true, true, "交接完成"),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("第 2 份电子档案");

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_item where archive_no like ?",
                                Long.class,
                                ARCHIVE_NO_PREFIX + "%"))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_intake_package_item "
                                        + "where intake_package_id = ?",
                                Long.class,
                                packageId))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from am_archive_intake_package where id = ?",
                                String.class,
                                packageId))
                .isEqualTo("PENDING_REVIEW");
    }

    private ArchiveIntakeManifestItem manifestItem(String suffix) {
        ArchiveIntakeManifestItem item = mock(ArchiveIntakeManifestItem.class);
        when(item.fondsCode()).thenReturn("F001");
        when(item.categoryCode()).thenReturn("WS");
        when(item.archiveNo()).thenReturn(ARCHIVE_NO_PREFIX + suffix);
        when(item.archiveYear()).thenReturn(2026);
        when(item.securityLevel()).thenReturn("公开");
        when(item.retentionPeriod()).thenReturn("永久");
        when(item.metadataFiles()).thenReturn(List.of());
        when(item.contentFiles()).thenReturn(List.of());
        return item;
    }

    private ArchiveItemDto item(Long id, String archiveNo) {
        return new ArchiveItemDto(
                id, null, "F001", "测试全宗", "WS", "文书", archiveNo, 3L, 4L, 2026, false, null, null,
                null, 1L, null);
    }
}
