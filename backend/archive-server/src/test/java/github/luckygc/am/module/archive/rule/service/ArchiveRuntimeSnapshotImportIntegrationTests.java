package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeDefinitionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotImportRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotPreflightRequest;
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
@DisplayName("运行时配置快照导入 PostgreSQL 集成")
class ArchiveRuntimeSnapshotImportIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private ArchiveRuntimeSnapshotService snapshotService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private ArchiveRuntimeDefinitionService definitionService;

    @BeforeEach
    void setUp() {
        ArchiveRuntimeSnapshotIntegrationSupport.seed(jdbcTemplate, false);
    }

    @Test
    @DisplayName("跨 ID 环境导入新草稿且中途失败时整批回滚")
    void importShouldCreateDraftAndRollbackWholeBatchOnFailure() {
        var snapshot =
                snapshotService.exportSnapshot(
                        ArchiveRuntimeSnapshotIntegrationSupport.SOURCE_VERSION_ID);
        var preflight =
                new ArchiveRuntimeSnapshotPreflightRequest(
                        snapshot,
                        ArchiveRuntimeSnapshotIntegrationSupport.SCHEME_CODE,
                        Map.of(),
                        Map.of());

        var imported =
                snapshotService.importAsDraft(
                        new ArchiveRuntimeSnapshotImportRequest(
                                preflight, "imported-v1", "跨环境导入草稿"),
                        9L);

        assertThat(imported.definitionCount()).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select status from am_archive_governance_scheme_version where id = ?",
                                String.class,
                                imported.schemeVersionId()))
                .isEqualTo("DRAFT");
        assertThat(
                        jdbcTemplate.queryForList(
                                "select definition_code from am_archive_runtime_definition "
                                        + "where scheme_version_id = ? and deleted_flag = false "
                                        + "order by definition_code",
                                String.class,
                                imported.schemeVersionId()))
                .containsExactly("actor-warning", "title-required");
        assertThat(
                        jdbcTemplate.queryForList(
                                "select distinct status from am_archive_runtime_definition "
                                        + "where scheme_version_id = ? and deleted_flag = false",
                                String.class,
                                imported.schemeVersionId()))
                .containsExactly("DRAFT");

        doAnswer(
                        invocation -> {
                            SaveArchiveRuntimeDefinitionRequest request = invocation.getArgument(0);
                            if (request.definitionCode().equals("title-required")) {
                                throw new IllegalStateException("模拟第二条定义导入失败");
                            }
                            return invocation.callRealMethod();
                        })
                .when(definitionService)
                .createDefinition(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());

        assertThatThrownBy(
                        () ->
                                snapshotService.importAsDraft(
                                        new ArchiveRuntimeSnapshotImportRequest(
                                                preflight, "rollback-v1", "应回滚"),
                                        9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("第二条定义");

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_governance_scheme_version "
                                        + "where scheme_id = ? and version_code = 'rollback-v1'",
                                Long.class,
                                ArchiveRuntimeSnapshotIntegrationSupport.SCHEME_ID))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_runtime_definition d "
                                        + "join am_archive_governance_scheme_version v "
                                        + "on v.id = d.scheme_version_id "
                                        + "where v.version_code = 'rollback-v1'",
                                Long.class))
                .isZero();
    }
}
