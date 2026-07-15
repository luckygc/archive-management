package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveExcelFile;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
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

        ArchiveExcelFile file = importExportService.exportItems(null, 9L);

        assertThat(file.bytes()).isNotEmpty();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_item_audit "
                                        + "where operation_type = 'EXPORT' and operated_by = 9",
                                Long.class))
                .isEqualTo(1L);
    }
}
