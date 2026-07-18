package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
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
@DisplayName("运行时配置 PostgreSQL 触发器")
class ArchiveRuntimeTriggerIntegrationTests extends PostgreSqlContainerTest {

    private static final long SCHEME_ID = 9_710_000L;

    @Autowired private JdbcTemplate jdbcTemplate;

    private long schemeVersionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) "
                        + "values (?, 'runtime-trigger', '运行时触发器测试方案')",
                SCHEME_ID);
        schemeVersionId =
                jdbcTemplate.queryForObject(
                        "insert into am_archive_governance_scheme_version "
                                + "(scheme_id, version_code, status) "
                                + "values (?, 'draft-v1', 'DRAFT') returning id",
                        Long.class,
                        SCHEME_ID);
    }

    @Test
    @DisplayName("同一事务先写动作再发布规则合法")
    void publishingRuleWithActionInSameTransactionSucceeds() {
        long definitionId = insertDraftRule("legal-rule");
        insertAction(definitionId);

        assertThatCode(
                        () -> {
                            publish(definitionId);
                            jdbcTemplate.execute("set constraints all immediate");
                        })
                .doesNotThrowAnyException();
        assertThat(status(definitionId)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("发布空动作规则由延迟约束拒绝")
    void publishingRuleWithoutActionIsRejected() {
        long definitionId = insertDraftRule("empty-rule");
        publish(definitionId);

        assertConstraintViolation(
                () -> jdbcTemplate.execute("set constraints all immediate"),
                "ck_am_archive_runtime_rule_requires_action");
    }

    @Test
    @DisplayName("已发布定义的语义字段不可原地修改")
    void publishedDefinitionIsImmutable() {
        long definitionId = insertPublishedRule("immutable-rule");

        assertConstraintViolation(
                () ->
                        jdbcTemplate.update(
                                "update am_archive_runtime_definition "
                                        + "set definition_name = '被篡改' where id = ?",
                                definitionId),
                "ck_am_archive_runtime_definition_published_immutable");
    }

    @Test
    @DisplayName("已发布规则动作不可删除")
    void publishedActionCannotBeDeleted() {
        long definitionId = insertPublishedRule("immutable-action");

        assertConstraintViolation(
                () ->
                        jdbcTemplate.update(
                                "delete from am_archive_runtime_action where definition_id = ?",
                                definitionId),
                "ck_am_archive_runtime_action_published_immutable");
    }

    @Test
    @DisplayName("已发布定义允许受审计启停")
    void publishedDefinitionCanBeDisabled() {
        long definitionId = insertPublishedRule("disable-rule");

        assertThat(
                        jdbcTemplate.update(
                                "update am_archive_runtime_definition "
                                        + "set enabled = false, updated_by = 42, updated_at = localtimestamp "
                                        + "where id = ?",
                                definitionId))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select enabled from am_archive_runtime_definition where id = ?",
                                Boolean.class,
                                definitionId))
                .isFalse();
    }

    private long insertPublishedRule(String code) {
        long definitionId = insertDraftRule(code);
        insertAction(definitionId);
        publish(definitionId);
        jdbcTemplate.execute("set constraints all immediate");
        jdbcTemplate.execute("set constraints all deferred");
        return definitionId;
    }

    private long insertDraftRule(String code) {
        return jdbcTemplate.queryForObject(
                "insert into am_archive_runtime_definition "
                        + "(scheme_version_id, definition_kind, definition_code, definition_name, "
                        + "trigger_point, condition_json, status) "
                        + "values (?, 'RULE', ?, ?, 'ITEM_BEFORE_CREATE', '{}'::jsonb, 'DRAFT') "
                        + "returning id",
                Long.class,
                schemeVersionId,
                code,
                "测试规则 " + code);
    }

    private void insertAction(long definitionId) {
        jdbcTemplate.update(
                "insert into am_archive_runtime_action "
                        + "(definition_id, action_type, action_order, action_params) "
                        + "values (?, 'WARN', 0, '{\"message\":\"测试警告\"}'::jsonb)",
                definitionId);
    }

    private void publish(long definitionId) {
        jdbcTemplate.update(
                "update am_archive_runtime_definition "
                        + "set status = 'PUBLISHED', published_at = localtimestamp where id = ?",
                definitionId);
    }

    private String status(long definitionId) {
        return jdbcTemplate.queryForObject(
                "select status from am_archive_runtime_definition where id = ?",
                String.class,
                definitionId);
    }

    private void assertConstraintViolation(Runnable operation, String constraintName) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .satisfies(
                        exception -> {
                            Throwable cause = exception;
                            while (cause != null && !(cause instanceof PSQLException)) {
                                cause = cause.getCause();
                            }
                            assertThat(cause).isInstanceOf(PSQLException.class);
                            PSQLException sqlException = (PSQLException) cause;
                            assertThat(sqlException.getSQLState()).isEqualTo("23514");
                            assertThat(sqlException.getServerErrorMessage().getConstraint())
                                    .isEqualTo(constraintName);
                        });
    }
}
