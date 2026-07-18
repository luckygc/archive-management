package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
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
@DisplayName("运行时配置 PostgreSQL 目标结构")
class ArchiveRuntimeSchemaIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("空库迁移只创建运行时定义动作和追踪表")
    void migrationCreatesOnlyRuntimeConfigurationTables() {
        List<String> tables =
                jdbcTemplate.queryForList(
                        "select table_name from information_schema.tables "
                                + "where table_schema = current_schema()",
                        String.class);

        assertThat(tables)
                .contains(
                        "am_archive_runtime_definition",
                        "am_archive_runtime_action",
                        "am_archive_runtime_trace")
                .noneMatch(
                        table ->
                                table.contains("ontology")
                                        || table.contains("field_semantic")
                                        || table.equals("am_archive_rule_effect"));
    }

    @Test
    @DisplayName("运行时表具有命名约束和稳定查询索引")
    void runtimeTablesHaveNamedConstraintsAndIndexes() {
        List<String> constraints =
                jdbcTemplate.queryForList(
                        "select conname from pg_constraint "
                                + "where connamespace = current_schema()::regnamespace",
                        String.class);
        List<String> indexes =
                jdbcTemplate.queryForList(
                        "select indexname from pg_indexes where schemaname = current_schema()",
                        String.class);

        assertThat(constraints)
                .contains(
                        "ck_am_archive_runtime_definition_kind",
                        "ck_am_archive_runtime_trigger_point",
                        "ck_am_archive_runtime_constraint_shape",
                        "ck_am_archive_runtime_action_type",
                        "ck_am_archive_runtime_action_params_object");
        assertThat(indexes)
                .contains(
                        "uk_am_archive_runtime_definition_code_active",
                        "idx_am_archive_runtime_definition_execution_active",
                        "idx_am_archive_runtime_action_definition_active",
                        "idx_am_archive_runtime_trace_object",
                        "idx_am_archive_runtime_trace_version");
        assertThat(columnType("am_archive_runtime_definition", "condition_json"))
                .isEqualTo("jsonb");
        assertThat(columnType("am_archive_runtime_action", "action_params")).isEqualTo("jsonb");
        assertThat(columnType("am_archive_runtime_trace", "action_json")).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("数据库拒绝未知触发点动作和遗留绑定类型")
    void databaseRejectsValuesOutsideFixedCatalogs() {
        long versionId = insertDraftVersion(9_700_000L);

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "insert into am_archive_runtime_definition "
                                                + "(scheme_version_id, definition_kind, definition_code, "
                                                + "definition_name, trigger_point, condition_json) "
                                                + "values (?, 'RULE', 'invalid-trigger', '无效触发点', "
                                                + "'USER_SCRIPT', '{}'::jsonb)",
                                        versionId))
                .hasStackTraceContaining("ck_am_archive_runtime_trigger_point");
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = ? and column_name = ?",
                String.class,
                tableName,
                columnName);
    }

    private long insertDraftVersion(long schemeId) {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) "
                        + "values (?, ?, ?)",
                schemeId,
                "runtime-schema-" + schemeId,
                "运行时结构测试方案");
        return jdbcTemplate.queryForObject(
                "insert into am_archive_governance_scheme_version "
                        + "(scheme_id, version_code, status) values (?, 'draft-v1', 'DRAFT') "
                        + "returning id",
                Long.class,
                schemeId);
    }
}
