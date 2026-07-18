package github.luckygc.am.module.archive.rule.service;

import org.springframework.jdbc.core.JdbcTemplate;

final class ArchiveRuntimeSnapshotIntegrationSupport {

    static final long SCHEME_ID = 9_670_000L;
    static final long SOURCE_VERSION_ID = 9_670_001L;
    static final long RESTORE_VERSION_ID = 9_670_002L;
    static final long ROLLBACK_VERSION_ID = 9_670_003L;
    static final long PUBLISHED_VERSION_ID = 9_670_004L;
    static final long CATEGORY_ID = 9_670_100L;
    static final String SCHEME_CODE = "portable-runtime";
    static final String CATEGORY_CODE = "SNAPSHOT_DOC";

    private ArchiveRuntimeSnapshotIntegrationSupport() {}

    static void seed(JdbcTemplate jdbcTemplate, boolean includeRestoreTargets) {
        Long classificationSchemeId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_classification_scheme "
                                + "where scheme_code = 'default_classification'",
                        Long.class);
        jdbcTemplate.update(
                "insert into am_archive_category "
                        + "(id, scheme_id, category_code, category_name, management_mode) "
                        + "values (?, ?, ?, '快照档案', 'ITEM_ONLY')",
                CATEGORY_ID,
                classificationSchemeId,
                CATEGORY_CODE);
        jdbcTemplate.update(
                "insert into am_archive_field "
                        + "(id, category_id, archive_level, field_scope, field_code, field_name, "
                        + "field_type, column_name, edit_control) "
                        + "values (?, ?, 'ITEM', 'METADATA', 'title', '题名', "
                        + "'TEXT', 'f_title', 'INPUT')",
                9_670_101L,
                CATEGORY_ID);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) "
                        + "values (?, ?, '可迁移运行时方案')",
                SCHEME_ID,
                SCHEME_CODE);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code, status) values (?, ?, 'source-v1', 'DRAFT')",
                SOURCE_VERSION_ID,
                SCHEME_ID);
        insertConstraint(
                jdbcTemplate,
                9_670_201L,
                SOURCE_VERSION_ID,
                "title-required",
                CATEGORY_CODE,
                "{\"not\":{\"field\":\"metadata.title\",\"operator\":\"IS_EMPTY\"}}",
                "题名不能为空");
        insertConstraint(
                jdbcTemplate,
                9_670_202L,
                SOURCE_VERSION_ID,
                "actor-warning",
                null,
                "{\"field\":\"context.userId\",\"operator\":\"GT\",\"value\":0}",
                "用户需要复核");
        if (!includeRestoreTargets) return;
        insertVersion(jdbcTemplate, RESTORE_VERSION_ID, "restore-v1", "DRAFT");
        insertVersion(jdbcTemplate, ROLLBACK_VERSION_ID, "rollback-v1", "DRAFT");
        insertVersion(jdbcTemplate, PUBLISHED_VERSION_ID, "published-v1", "PUBLISHED");
        insertConstraint(
                jdbcTemplate,
                9_670_211L,
                RESTORE_VERSION_ID,
                "old-restore-rule",
                null,
                "{\"field\":\"context.userId\",\"operator\":\"GT\",\"value\":0}",
                "旧恢复配置");
        insertConstraint(
                jdbcTemplate,
                9_670_212L,
                ROLLBACK_VERSION_ID,
                "old-rollback-rule",
                null,
                "{\"field\":\"context.userId\",\"operator\":\"GT\",\"value\":0}",
                "旧回滚配置");
    }

    private static void insertVersion(
            JdbcTemplate jdbcTemplate, long versionId, String versionCode, String status) {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code, status) values (?, ?, ?, ?)",
                versionId,
                SCHEME_ID,
                versionCode,
                status);
    }

    private static void insertConstraint(
            JdbcTemplate jdbcTemplate,
            long id,
            long versionId,
            String code,
            String categoryCode,
            String condition,
            String message) {
        jdbcTemplate.update(
                "insert into am_archive_runtime_definition "
                        + "(id, scheme_version_id, definition_kind, definition_code, "
                        + "definition_name, trigger_point, scope_category_code, "
                        + "scope_archive_level, priority, condition_json, constraint_action, "
                        + "constraint_message, status) "
                        + "values (?, ?, 'CONSTRAINT', ?, ?, 'ITEM_BEFORE_CREATE', ?, "
                        + "'ITEM', 10, ?::jsonb, 'REJECT', ?, 'DRAFT')",
                id,
                versionId,
                code,
                code,
                categoryCode,
                condition,
                message);
    }
}
