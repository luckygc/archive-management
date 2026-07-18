package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

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
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;
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
@DisplayName("档案同步导出事务")
class ArchiveItemExportTransactionIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private ArchiveItemImportExportService importExportService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArchiveItemQueryService queryService;
    @MockitoBean private AuthorizationPermissionService permissionService;
    @MockitoBean private StorageObjectService storageObjectService;
    @MockitoBean private FileLinkService fileLinkService;

    @AfterEach
    void cleanExportAudit() {
        jdbcTemplate.update(
                "delete from am_archive_item_audit "
                        + "where operated_by = 9 and operation_type = 'EXPORT'");
    }

    @Test
    @DisplayName("同步导出在可写事务中持久化操作审计")
    void exportItemsShouldPersistAuditInWritableTransaction() {
        when(permissionService.hasPermission(9L, AuthorizationPermissionCode.ARCHIVE_EXPORT.code()))
                .thenReturn(true);
        when(queryService.searchItems(any(), eq(9L)))
                .thenReturn(
                        new ArchiveItemQueryService.ArchiveItemListDto(
                                null,
                                List.of(),
                                CursorPageResponse.withCursorValues(
                                        List.of(), 1000, null, null, null, null, null)));

        stubStorageAndLink();

        var link = importExportService.createExportDownloadLink(null, 9L);

        assertThat(link.code()).isEqualTo("export-code");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_item_audit "
                                        + "where operation_type = 'EXPORT' and operated_by = 9",
                                Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("短链创建失败时回滚导出审计")
    void createExportDownloadLinkShouldRollbackAuditWhenLinkCreationFails() {
        when(permissionService.hasPermission(9L, AuthorizationPermissionCode.ARCHIVE_EXPORT.code()))
                .thenReturn(true);
        when(queryService.searchItems(any(), eq(9L)))
                .thenReturn(
                        new ArchiveItemQueryService.ArchiveItemListDto(
                                null,
                                List.of(),
                                CursorPageResponse.withCursorValues(
                                        List.of(), 1000, null, null, null, null, null)));
        when(storageObjectService.storeObject(any(), eq(9L)))
                .thenReturn(
                        new StorageObjectDto(
                                20L, "archive", "key", "archive-export.xlsx", 3, null, null, 9L));
        when(fileLinkService.createUserLinkUntil(
                        eq(FileLinkTargetType.STORAGE_OBJECT),
                        eq(null),
                        eq(20L),
                        any(LocalDateTime.class),
                        eq(9L)))
                .thenThrow(new IllegalStateException("短链创建失败"));

        assertThatThrownBy(() -> importExportService.createExportDownloadLink(null, 9L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_item_audit "
                                        + "where operation_type = 'EXPORT' and operated_by = 9",
                                Long.class))
                .isZero();
    }

    private void stubStorageAndLink() {
        when(storageObjectService.storeObject(any(), eq(9L)))
                .thenReturn(
                        new StorageObjectDto(
                                20L, "archive", "key", "archive-export.xlsx", 3, null, null, 9L));
        when(fileLinkService.createUserLinkUntil(
                        eq(FileLinkTargetType.STORAGE_OBJECT),
                        eq(null),
                        eq(20L),
                        any(LocalDateTime.class),
                        eq(9L)))
                .thenReturn(
                        new FileLinkService.FileLinkCreated(
                                "export-code", LocalDateTime.of(2026, 7, 15, 10, 10)));
    }
}
