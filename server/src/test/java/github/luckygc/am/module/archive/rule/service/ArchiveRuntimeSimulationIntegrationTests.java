package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
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
@DisplayName("运行时配置试运行 PostgreSQL 集成")
class ArchiveRuntimeSimulationIntegrationTests extends PostgreSqlContainerTest {

    private static final long SCHEME_ID = 9_620_000L;
    private static final long VERSION_ID = 9_620_001L;

    @Autowired private ArchiveRuntimeExecutionService executionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) values (?, ?, ?)",
                SCHEME_ID,
                "runtime-simulation-scheme",
                "运行时试运行方案");
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code) values (?, ?, ?)",
                VERSION_ID,
                SCHEME_ID,
                "v1");
        jdbcTemplate.update(
                "insert into am_archive_runtime_definition "
                        + "(scheme_version_id, definition_kind, definition_code, definition_name, "
                        + "trigger_point, condition_json, constraint_action, constraint_message, "
                        + "status, published_by, published_at) "
                        + "values (?, 'CONSTRAINT', 'year-2026', '年度检查', "
                        + "'ITEM_BEFORE_CREATE', "
                        + "'{\"field\":\"item.archiveYear\",\"operator\":\"EQ\",\"value\":2026}'::jsonb, "
                        + "'WARN', '年度不是 2026', 'PUBLISHED', 7, localtimestamp)",
                VERSION_ID);
    }

    @Test
    @DisplayName("试运行返回真实决策但不写主数据、审计或追踪")
    void simulationIsSideEffectFree() {
        long itemCount = count("am_archive_item");
        long auditCount = count("am_archive_item_audit");
        long traceCount = count("am_archive_runtime_trace");

        var result =
                executionService.simulate(
                        new ArchiveRuntimeExecutionRequest(
                                VERSION_ID,
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                                "F001",
                                null,
                                ArchiveLevel.ITEM,
                                "ARCHIVE_ITEM",
                                null,
                                Map.of("item.archiveYear", 2025),
                                7L));

        assertThat(result.decisions())
                .extracting(decision -> decision.definitionCode())
                .containsExactly("year-2026");
        assertThat(result.warnings())
                .extracting(warning -> warning.definitionCode())
                .containsExactly("year-2026");
        assertThat(result.blocking()).isFalse();
        assertThat(count("am_archive_item")).isEqualTo(itemCount);
        assertThat(count("am_archive_item_audit")).isEqualTo(auditCount);
        assertThat(count("am_archive_runtime_trace")).isEqualTo(traceCount);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
    }
}
