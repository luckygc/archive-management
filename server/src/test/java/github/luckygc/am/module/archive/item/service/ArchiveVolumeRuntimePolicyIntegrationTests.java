package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

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
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.CreateArchiveVolumeRequest;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
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
@DisplayName("案卷运行时策略 PostgreSQL 集成")
class ArchiveVolumeRuntimePolicyIntegrationTests extends PostgreSqlContainerTest {

    private static final long CATEGORY_ID = 9_640_000L;
    private static final long SCHEME_ID = 9_641_000L;
    private static final long VERSION_ID = 9_641_001L;
    private static final long VOLUME_ID = 9_642_001L;
    private static final long ITEM_ID = 9_642_002L;

    @Autowired private ArchiveVolumeService volumeService;
    @Autowired private ArchiveRuntimeFieldCatalogService fieldCatalogService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArchiveCategoryService categoryService;
    @MockitoBean private ArchiveMetadataReferenceService metadataReferenceService;
    @MockitoBean private ArchiveGovernanceService governanceService;
    @MockitoBean private ArchiveDataScopeService dataScopeService;
    @MockitoBean private AuthorizationPermissionService permissionService;

    private ArchiveCategoryDto category;

    @BeforeEach
    void setUp() {
        seedConfiguration();
        category = category();
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        when(categoryService.getCategory(CATEGORY_ID)).thenReturn(category);
        when(categoryService.listCategories(null)).thenReturn(List.of(category));
        when(metadataReferenceService.getEnabledFondsByCode("F001")).thenReturn(fonds());
        when(dataScopeService.buildItemFilter(9L, CATEGORY_ID, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(VERSION_ID);
        when(governanceService.requireDefaultVersionForNewArchive("F001", "VOLUME_DOC"))
                .thenReturn(version);
    }

    @Test
    @DisplayName("案卷创建阻断时案卷和追踪均不产生")
    void blockedVolumeCreateLeavesNoWrites() {
        insertConstraint(
                ArchiveRuntimeTriggerPoint.VOLUME_BEFORE_CREATE,
                "volume-create-block",
                "volume.archiveNo",
                "ALLOWED");

        assertThatThrownBy(
                        () ->
                                volumeService.createVolume(
                                        new CreateArchiveVolumeRequest(
                                                CATEGORY_ID, "F001", "BLOCKED", 2026, "DRAFT"),
                                        9L))
                .hasMessageContaining("volume-create-block");

        assertThat(count("am_archive_volume")).isZero();
        assertThat(count("am_archive_runtime_trace")).isZero();
    }

    @Test
    @DisplayName("条目入卷阻断时归属和显示顺序保持不变")
    void blockedAddItemLeavesMembershipUnchanged() {
        seedVolumeAndItem();
        insertConstraint(
                ArchiveRuntimeTriggerPoint.VOLUME_BEFORE_ADD_ITEM,
                "volume-member-year",
                "item.archiveYear",
                2030);

        assertThatThrownBy(() -> volumeService.addItemToVolume(VOLUME_ID, ITEM_ID, 88, 9L))
                .hasMessageContaining("volume-member-year");

        var row =
                jdbcTemplate.queryForMap(
                        "select volume_id, display_order from am_archive_item where id = ?",
                        ITEM_ID);
        assertThat(row.get("volume_id")).isNull();
        assertThat(((Number) row.get("display_order")).intValue()).isZero();
        assertThat(count("am_archive_runtime_trace")).isZero();
    }

    private void seedConfiguration() {
        Long classificationSchemeId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_classification_scheme "
                                + "where scheme_code = 'default_classification'",
                        Long.class);
        jdbcTemplate.update(
                "insert into am_archive_category "
                        + "(id, scheme_id, category_code, category_name, management_mode, table_status) "
                        + "values (?, ?, 'VOLUME_DOC', '案卷档案', 'VOLUME_ITEM', 'NOT_BUILT')",
                CATEGORY_ID,
                classificationSchemeId);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) "
                        + "values (?, 'runtime-volume-scheme', '案卷运行时方案')",
                SCHEME_ID);
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code) values (?, ?, 'v1')",
                VERSION_ID,
                SCHEME_ID);
    }

    private void seedVolumeAndItem() {
        jdbcTemplate.update(
                "insert into am_archive_volume "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "electronic_status, archive_year, governance_scheme_version_id) "
                        + "values (?, 'F001', '测试全宗', 'VOLUME_DOC', '案卷档案', "
                        + "'V-001', 'DRAFT', 2026, ?)",
                VOLUME_ID,
                VERSION_ID);
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "electronic_status, archive_year, governance_scheme_version_id) "
                        + "values (?, 'F001', '测试全宗', 'VOLUME_DOC', '案卷档案', "
                        + "'I-001', 'DRAFT', 2026, ?)",
                ITEM_ID,
                VERSION_ID);
    }

    private void insertConstraint(
            ArchiveRuntimeTriggerPoint triggerPoint,
            String code,
            String field,
            Object expectedValue) {
        String signature =
                fieldCatalogService.catalog(VERSION_ID, "VOLUME_DOC", triggerPoint).signature();
        jdbcTemplate.update(
                "insert into am_archive_runtime_definition "
                        + "(scheme_version_id, definition_kind, definition_code, definition_name, "
                        + "trigger_point, scope_category_code, scope_archive_level, condition_json, "
                        + "constraint_action, constraint_message, status, field_catalog_signature, "
                        + "published_by, published_at) "
                        + "values (?, 'CONSTRAINT', ?, ?, ?, 'VOLUME_DOC', 'VOLUME', "
                        + "jsonb_build_object('field', ?, 'operator', 'EQ', 'value', ?), "
                        + "'REJECT', '案卷运行时检查未通过', 'PUBLISHED', ?, 9, localtimestamp)",
                VERSION_ID,
                code,
                code,
                triggerPoint.name(),
                field,
                expectedValue,
                signature);
    }

    private ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ArchiveCategoryDto(
                CATEGORY_ID,
                1L,
                null,
                "VOLUME_DOC",
                "案卷档案",
                ArchiveManagementMode.VOLUME_ITEM,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                now,
                now);
    }

    private ArchiveFondsDto fonds() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "测试全宗", true, 0, now, now);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
    }
}
