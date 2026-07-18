package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService;
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
@Transactional
@DisplayName("档案条目运行时策略 PostgreSQL 集成")
class ArchiveItemRuntimePolicyIntegrationTests extends PostgreSqlContainerTest {

    private static final long CATEGORY_ID = 9_630_000L;
    private static final long SCHEME_ID = 9_631_000L;
    private static final long VERSION_ID = 9_631_001L;
    private static final String DYNAMIC_TABLE = "am_archive_item_runtime_doc";

    @Autowired private ArchiveItemCommandService commandService;
    @Autowired private ArchiveRuntimeFieldCatalogService fieldCatalogService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArchiveMetadataService metadataService;
    @MockitoBean private ArchiveMetadataReferenceService metadataReferenceService;
    @MockitoBean private ArchiveCategoryService categoryService;
    @MockitoBean private ArchiveGovernanceService governanceService;
    @MockitoBean private ArchiveDataScopeService dataScopeService;
    @MockitoBean private AuthorizationPermissionService permissionService;
    @MockitoBean private ArchiveItemSearchProjectionService searchProjectionService;

    private ArchiveCategoryDto category;
    private ArchiveFieldDto titleField;

    @BeforeEach
    void setUp() {
        seedCategoryAndGovernance();
        category = category();
        titleField = titleField();
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        when(categoryService.getCategory(CATEGORY_ID)).thenReturn(category);
        when(categoryService.listCategories(null)).thenReturn(List.of(category));
        when(metadataReferenceService.getEnabledFondsByCode("F001")).thenReturn(fonds());
        when(metadataService.listEnabledFields(CATEGORY_ID, ArchiveLevel.ITEM))
                .thenReturn(List.of(titleField));
        when(metadataService.listEnabledFields(
                        CATEGORY_ID, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL))
                .thenReturn(List.of());
        when(dataScopeService.buildItemFilter(9L, CATEGORY_ID, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(VERSION_ID);
        when(governanceService.requireDefaultVersionForNewArchive("F001", "RUNTIME_DOC"))
                .thenReturn(version);
        seedSetFieldAndWarningDefinitions();
    }

    @Test
    @DisplayName("字段赋值回灌动态表，警告放行并与审计一起追踪")
    void assignmentAndWarningCommitWithAuditAndTrace() {
        var item = commandService.createItem(request("A-001", 2025), 9L);

        assertThat(item.archiveYear()).isEqualTo(2026);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select f_title from " + DYNAMIC_TABLE + " where id = ?",
                                String.class,
                                item.id()))
                .isEqualTo("规则补齐标题");
        assertThat(count("am_archive_item_audit")).isEqualTo(1L);
        assertThat(count("am_archive_runtime_trace")).isEqualTo(2L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from am_archive_runtime_trace "
                                        + "where severity = 'WARNING' and blocking_flag = false",
                                Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("阻断发生在主表、动态表、审计和追踪之前")
    void blockingConstraintLeavesNoPartialWrites() {
        insertConstraint("must-be-allowed", "A-ALLOWED", "REJECT", "档号不允许");

        assertThatThrownBy(() -> commandService.createItem(request("A-BLOCKED", 2025), 9L))
                .hasMessageContaining("must-be-allowed")
                .hasMessageContaining("档号不允许");

        assertThat(count("am_archive_item")).isZero();
        assertThat(count(DYNAMIC_TABLE)).isZero();
        assertThat(count("am_archive_item_audit")).isZero();
        assertThat(count("am_archive_runtime_trace")).isZero();
    }

    private void seedCategoryAndGovernance() {
        Long classificationSchemeId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_classification_scheme "
                                + "where scheme_code = 'default_classification'",
                        Long.class);
        jdbcTemplate.update(
                "insert into am_archive_category "
                        + "(id, scheme_id, category_code, category_name, management_mode, "
                        + "item_table_name, table_status) values (?, ?, ?, ?, 'ITEM_ONLY', ?, 'BUILT')",
                CATEGORY_ID,
                classificationSchemeId,
                "RUNTIME_DOC",
                "运行时档案",
                DYNAMIC_TABLE);
        jdbcTemplate.execute(
                "create table "
                        + DYNAMIC_TABLE
                        + " (id bigint primary key, f_title varchar(100), "
                        + "deleted_flag boolean not null default false, created_at timestamp not null, "
                        + "updated_at timestamp not null)");
        jdbcTemplate.update(
                "insert into am_archive_field "
                        + "(id, category_id, archive_level, field_scope, field_code, field_name, "
                        + "field_type, column_name, text_length, edit_control) "
                        + "values (?, ?, 'ITEM', 'METADATA', 'title', '题名', 'TEXT', "
                        + "'f_title', 100, 'INPUT')",
                9_630_001L,
                CATEGORY_ID);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) "
                        + "values (?, 'runtime-item-scheme', '条目运行时方案')",
                SCHEME_ID);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code) values (?, ?, 'v1')",
                VERSION_ID,
                SCHEME_ID);
    }

    private void seedSetFieldAndWarningDefinitions() {
        String signature =
                fieldCatalogService
                        .catalog(
                                VERSION_ID,
                                "RUNTIME_DOC",
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE)
                        .signature();
        long ruleId =
                insertRule(
                        "normalize-item",
                        "{\"field\":\"item.archiveYear\",\"operator\":\"EQ\",\"value\":2025}",
                        signature);
        jdbcTemplate.update(
                "insert into am_archive_runtime_action "
                        + "(definition_id, action_type, action_params) values (?, 'SET_FIELD', "
                        + "'{\"field\":\"item.archiveYear\",\"value\":2026}'::jsonb), "
                        + "(?, 'SET_FIELD', "
                        + "'{\"field\":\"metadata.title\",\"value\":\"规则补齐标题\"}'::jsonb)",
                ruleId,
                ruleId);
        jdbcTemplate.update(
                "update am_archive_runtime_definition set status = 'PUBLISHED', "
                        + "field_catalog_signature = ?, published_by = 9, published_at = localtimestamp "
                        + "where id = ?",
                signature,
                ruleId);
        insertConstraint("archive-no-warning", "A-EXPECTED", "WARN", "档号需要复核");
    }

    private long insertRule(String code, String conditionJson, String signature) {
        return jdbcTemplate.queryForObject(
                "insert into am_archive_runtime_definition "
                        + "(scheme_version_id, definition_kind, definition_code, definition_name, "
                        + "trigger_point, scope_category_code, scope_archive_level, priority, "
                        + "condition_json, field_catalog_signature) "
                        + "values (?, 'RULE', ?, ?, 'ITEM_BEFORE_CREATE', 'RUNTIME_DOC', "
                        + "'ITEM', 10, ?::jsonb, ?) returning id",
                Long.class,
                VERSION_ID,
                code,
                code,
                conditionJson,
                signature);
    }

    private void insertConstraint(
            String code, String expectedArchiveNo, String action, String message) {
        String signature =
                fieldCatalogService
                        .catalog(
                                VERSION_ID,
                                "RUNTIME_DOC",
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE)
                        .signature();
        jdbcTemplate.update(
                "insert into am_archive_runtime_definition "
                        + "(scheme_version_id, definition_kind, definition_code, definition_name, "
                        + "trigger_point, scope_category_code, scope_archive_level, priority, "
                        + "condition_json, constraint_action, constraint_message, status, "
                        + "field_catalog_signature, published_by, published_at) "
                        + "values (?, 'CONSTRAINT', ?, ?, 'ITEM_BEFORE_CREATE', 'RUNTIME_DOC', "
                        + "'ITEM', 20, jsonb_build_object('field', 'item.archiveNo', "
                        + "'operator', 'EQ', 'value', ?), ?, ?, 'PUBLISHED', ?, 9, localtimestamp)",
                VERSION_ID,
                code,
                code,
                expectedArchiveNo,
                action,
                message,
                signature);
    }

    private CreateArchiveItemRequest request(String archiveNo, int archiveYear) {
        return new CreateArchiveItemRequest(
                CATEGORY_ID,
                null,
                "F001",
                archiveNo,
                archiveYear,
                "DRAFT",
                null,
                null,
                null,
                Map.of("title", "用户标题"));
    }

    private ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ArchiveCategoryDto(
                CATEGORY_ID,
                1L,
                null,
                "RUNTIME_DOC",
                "运行时档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                DYNAMIC_TABLE,
                null,
                null,
                ArchiveTableStatus.BUILT,
                now,
                true,
                0,
                now,
                now);
    }

    private ArchiveFieldDto titleField() {
        return new ArchiveFieldDto(
                9_630_001L,
                CATEGORY_ID,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                "title",
                "题名",
                ArchiveFieldType.TEXT,
                "f_title",
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                true,
                false,
                true,
                0,
                null,
                null,
                null);
    }

    private ArchiveFondsDto fonds() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "测试全宗", true, 0, now, now);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
    }
}
