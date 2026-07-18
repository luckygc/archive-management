package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecisionSeverity;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeBlockedException;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeWarning;
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
@Transactional
@DisplayName("档案导出运行时策略 PostgreSQL 集成")
class ArchiveExportRuntimePolicyIntegrationTests extends PostgreSqlContainerTest {

    private static final long SCHEME_ID = 9_650_000L;
    private static final long VERSION_ID = 9_650_001L;
    private static final long ITEM_ID = 9_650_002L;
    private static final long USER_ID = 9_650_009L;

    @Autowired private ArchiveItemImportExportService importExportService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArchiveItemQueryService queryService;
    @MockitoBean private AuthorizationPermissionService permissionService;
    @MockitoBean private StorageObjectService storageObjectService;
    @MockitoBean private FileLinkService fileLinkService;
    @MockitoBean private ArchiveRuntimeExecutionService runtimeExecutionService;

    @BeforeEach
    void setUp() {
        seedGovernanceAndItem();
        when(permissionService.hasPermission(USER_ID, "archive:export")).thenReturn(true);
        when(queryService.searchItems(any(), eq(USER_ID)))
                .thenReturn(
                        new ArchiveItemQueryService.ArchiveItemListDto(
                                null,
                                List.of(),
                                CursorPageResponse.withCursorValues(
                                        List.of(exportRow()), 1000, null, null, null, null, null)));
    }

    @Test
    @DisplayName("导出前阻断时不生成文件、不写审计和追踪")
    void blockingPolicyShouldLeaveNoExportSideEffects() {
        when(runtimeExecutionService.enforce(any()))
                .thenThrow(new ArchiveRuntimeBlockedException(blockingResult()));

        assertThatThrownBy(() -> importExportService.createExportDownloadLink(null, USER_ID))
                .isInstanceOf(ArchiveRuntimeBlockedException.class)
                .hasMessageContaining("export-block");

        verify(storageObjectService, never()).storeObject(any(), eq(USER_ID));
        verify(fileLinkService, never()).createUserLinkUntil(any(), any(), any(), any(), any());
        assertThat(count("am_archive_item_audit")).isZero();
        assertThat(count("am_archive_runtime_trace")).isZero();
    }

    @Test
    @DisplayName("导出警告放行并在文件与审计成功后写入追踪")
    void warningPolicyShouldReturnWarningAndPersistTrace() {
        when(runtimeExecutionService.enforce(any(ArchiveRuntimeExecutionRequest.class)))
                .thenAnswer(
                        invocation -> {
                            ArchiveRuntimeExecutionRequest request = invocation.getArgument(0);
                            return warningResult(request);
                        });
        when(storageObjectService.storeObject(any(), eq(USER_ID)))
                .thenReturn(
                        new StorageObjectDto(
                                20L,
                                "archive",
                                "key",
                                "archive-export.xlsx",
                                3,
                                "xlsx",
                                null,
                                USER_ID));
        when(fileLinkService.createUserLinkUntil(
                        eq(FileLinkTargetType.STORAGE_OBJECT),
                        eq(null),
                        eq(20L),
                        any(LocalDateTime.class),
                        eq(USER_ID)))
                .thenReturn(
                        new FileLinkService.FileLinkCreated(
                                "export-code", LocalDateTime.of(2026, 7, 18, 10, 10)));

        var result = importExportService.createExportDownloadLink(null, USER_ID);

        assertThat(result.code()).isEqualTo("export-code");
        assertThat(result.warnings())
                .containsExactly(new ArchiveRuntimeWarning("export-warning", "导出量较大"));
        assertThat(count("am_archive_item_audit")).isEqualTo(1L);
        assertThat(count("am_archive_runtime_trace")).isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select trigger_point from am_archive_runtime_trace "
                                        + "where scheme_version_id = ?",
                                String.class,
                                VERSION_ID))
                .isEqualTo("EXPORT_BEFORE_CREATE");
    }

    private void seedGovernanceAndItem() {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) "
                        + "values (?, 'runtime-export-scheme', '导出运行时方案')",
                SCHEME_ID);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code) values (?, ?, 'v1')",
                VERSION_ID,
                SCHEME_ID);
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "electronic_status, archive_year, governance_scheme_version_id) "
                        + "values (?, 'F001', '全宗', 'CONTRACT', '合同', 'A-001', "
                        + "'DRAFT', 2026, ?)",
                ITEM_ID,
                VERSION_ID);
    }

    private static Map<String, Object> exportRow() {
        return Map.of(
                "id", ITEM_ID,
                "fondsCode", "F001",
                "fondsName", "全宗",
                "categoryCode", "CONTRACT",
                "categoryName", "合同",
                "archiveNo", "A-001",
                "archiveYear", 2026);
    }

    private static ArchiveRuntimeExecutionResult blockingResult() {
        ArchiveRuntimeDecision decision =
                new ArchiveRuntimeDecision(
                        null,
                        "export-block",
                        ArchiveRuntimeDefinitionKind.CONSTRAINT,
                        false,
                        List.of(),
                        "当前范围禁止导出",
                        ArchiveRuleDecisionSeverity.ERROR,
                        true,
                        "约束断言未满足");
        return new ArchiveRuntimeExecutionResult(
                Map.of(), Map.of(), List.of(decision), List.of(), true);
    }

    private static ArchiveRuntimeExecutionResult warningResult(
            ArchiveRuntimeExecutionRequest request) {
        ArchiveRuntimeDecision decision =
                new ArchiveRuntimeDecision(
                        null,
                        "export-warning",
                        ArchiveRuntimeDefinitionKind.RULE,
                        true,
                        List.of(
                                new ArchiveRuntimeActionDecision(
                                        ArchiveRuntimeActionType.WARN, Map.of("message", "导出量较大"))),
                        "导出量较大",
                        ArchiveRuleDecisionSeverity.WARNING,
                        false,
                        null);
        return new ArchiveRuntimeExecutionResult(
                request.candidateFacts(),
                Map.of(),
                List.of(decision),
                List.of(new ArchiveRuntimeWarning("export-warning", "导出量较大")),
                false);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
    }
}
