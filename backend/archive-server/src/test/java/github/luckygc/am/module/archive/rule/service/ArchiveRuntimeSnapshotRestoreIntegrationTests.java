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
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeDefinitionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotPreflightRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotRestoreRequest;
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
@DisplayName("运行时配置快照恢复 PostgreSQL 集成")
class ArchiveRuntimeSnapshotRestoreIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private ArchiveRuntimeSnapshotService snapshotService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private ArchiveRuntimeDefinitionService definitionService;

    @BeforeEach
    void setUp() {
        ArchiveRuntimeSnapshotIntegrationSupport.seed(jdbcTemplate, true);
    }

    @Test
    @DisplayName("草稿全量替换、失败回滚及非草稿拒绝保持原配置")
    void restoreShouldReplaceDraftAtomicallyAndRejectNonDraft() {
        var snapshot =
                snapshotService.exportSnapshot(
                        ArchiveRuntimeSnapshotIntegrationSupport.SOURCE_VERSION_ID);
        var preflight =
                new ArchiveRuntimeSnapshotPreflightRequest(
                        snapshot,
                        ArchiveRuntimeSnapshotIntegrationSupport.SCHEME_CODE,
                        Map.of(),
                        Map.of());

        var restored =
                snapshotService.restoreDraft(
                        ArchiveRuntimeSnapshotIntegrationSupport.RESTORE_VERSION_ID,
                        new ArchiveRuntimeSnapshotRestoreRequest(preflight),
                        9L);

        assertThat(restored.beforeDefinitionCount()).isEqualTo(1);
        assertThat(restored.afterDefinitionCount()).isEqualTo(2);
        assertThat(
                        activeDefinitionCodes(
                                ArchiveRuntimeSnapshotIntegrationSupport.RESTORE_VERSION_ID))
                .containsExactly("actor-warning", "title-required");

        doAnswer(
                        invocation -> {
                            SaveArchiveRuntimeDefinitionRequest request = invocation.getArgument(0);
                            if (request.definitionCode().equals("title-required")) {
                                throw new IllegalStateException("模拟恢复中途失败");
                            }
                            return invocation.callRealMethod();
                        })
                .when(definitionService)
                .createDefinition(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());

        assertThatThrownBy(
                        () ->
                                snapshotService.restoreDraft(
                                        ArchiveRuntimeSnapshotIntegrationSupport
                                                .ROLLBACK_VERSION_ID,
                                        new ArchiveRuntimeSnapshotRestoreRequest(preflight),
                                        9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("中途失败");
        assertThat(
                        activeDefinitionCodes(
                                ArchiveRuntimeSnapshotIntegrationSupport.ROLLBACK_VERSION_ID))
                .containsExactly("old-rollback-rule");

        assertThatThrownBy(
                        () ->
                                snapshotService.restoreDraft(
                                        ArchiveRuntimeSnapshotIntegrationSupport
                                                .PUBLISHED_VERSION_ID,
                                        new ArchiveRuntimeSnapshotRestoreRequest(preflight),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("草稿");
        assertThat(
                        activeDefinitionCodes(
                                ArchiveRuntimeSnapshotIntegrationSupport.PUBLISHED_VERSION_ID))
                .isEmpty();
    }

    private java.util.List<String> activeDefinitionCodes(long versionId) {
        return jdbcTemplate.queryForList(
                "select definition_code from am_archive_runtime_definition "
                        + "where scheme_version_id = ? and deleted_flag = false "
                        + "order by definition_code",
                String.class,
                versionId);
    }
}
