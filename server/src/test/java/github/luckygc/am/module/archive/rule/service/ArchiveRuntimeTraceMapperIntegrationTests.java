package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeTraceSearchCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeTraceSearchCriteria.ArchiveRuntimeTracePageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveRuntimeTraceSearchCriteria.ArchiveRuntimeTraceTargetScope;
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
@DisplayName("运行时追踪 MyBatis PostgreSQL 集成")
class ArchiveRuntimeTraceMapperIntegrationTests extends PostgreSqlContainerTest {

    private static final long SCHEME_ID = 9_610_000L;
    private static final long VERSION_ID = 9_610_001L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 18, 10, 0);

    @Autowired private ArchiveRuleMapper ruleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) values (?, ?, ?)",
                SCHEME_ID,
                "runtime-trace-scheme",
                "运行时追踪方案");
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version "
                        + "(id, scheme_id, version_code) values (?, ?, ?)",
                VERSION_ID,
                SCHEME_ID,
                "v1");
    }

    @Test
    @DisplayName("相同创建时间按 ID 稳定游标翻页且无重复遗漏")
    void stableCursorUsesCreatedAtAndId() {
        insertTrace(9_611_001L, "PROCESS", null, 7L, BASE_TIME);
        insertTrace(9_611_002L, "PROCESS", null, 7L, BASE_TIME);
        insertTrace(9_611_003L, "PROCESS", null, 7L, BASE_TIME);

        List<Map<String, Object>> first =
                ruleMapper.listRuntimeTraces(criteria(true, List.of(), page(null, null, 3)));
        List<Map<String, Object>> second =
                ruleMapper.listRuntimeTraces(
                        criteria(true, List.of(), page(BASE_TIME, 9_611_002L, 3)));

        assertThat(ids(first).subList(0, 2)).containsExactly(9_611_003L, 9_611_002L);
        assertThat(ids(second)).containsExactly(9_611_001L);
    }

    @Test
    @DisplayName("数据范围在 limit 前过滤且非档案对象只对创建人可见")
    void visibilityFiltersItemsAndNonArchiveObjects() {
        insertItem(9_612_001L, "F001", "DOC");
        insertItem(9_612_002L, "F002", "DOC");
        insertTrace(9_613_001L, "ARCHIVE_ITEM", 9_612_001L, 8L, BASE_TIME.plusSeconds(4));
        insertTrace(9_613_002L, "ARCHIVE_ITEM", 9_612_002L, 8L, BASE_TIME.plusSeconds(3));
        insertTrace(9_613_003L, "PROCESS", null, 8L, BASE_TIME.plusSeconds(2));
        insertTrace(9_613_004L, "PROCESS", null, 7L, BASE_TIME.plusSeconds(1));
        ArchiveRuntimeTraceTargetScope scope =
                new ArchiveRuntimeTraceTargetScope(
                        "DOC",
                        "am_archive_item_doc",
                        List.of(
                                new ArchiveDataScopeSqlGroup(
                                        List.of("F001"), List.of(), List.of(), List.of())));

        List<Map<String, Object>> rows =
                ruleMapper.listRuntimeTraces(criteria(false, List.of(scope), page(null, null, 10)));

        assertThat(ids(rows)).containsExactly(9_613_001L, 9_613_004L);
    }

    private ArchiveRuntimeTraceSearchCriteria criteria(
            boolean allData,
            List<ArchiveRuntimeTraceTargetScope> itemScopes,
            ArchiveRuntimeTracePageWindow page) {
        return new ArchiveRuntimeTraceSearchCriteria(
                null, null, null, null, null, allData, 7L, itemScopes, List.of(), page);
    }

    private ArchiveRuntimeTracePageWindow page(LocalDateTime createdAt, Long id, int rowLimit) {
        return new ArchiveRuntimeTracePageWindow(false, createdAt, id, rowLimit);
    }

    private void insertTrace(
            long id, String objectType, Long objectId, long createdBy, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "insert into am_archive_runtime_trace "
                        + "(id, scheme_version_id, trigger_point, object_type_code, object_id, "
                        + "created_by, created_at) values (?, ?, 'ITEM_BEFORE_CREATE', ?, ?, ?, ?)",
                id,
                VERSION_ID,
                objectType,
                objectId,
                createdBy,
                createdAt);
    }

    private void insertItem(long id, String fondsCode, String categoryCode) {
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, "
                        + "electronic_status, archive_year, governance_scheme_version_id) "
                        + "values (?, ?, '全宗', ?, '分类', 'DRAFT', 2026, ?)",
                id,
                fondsCode,
                categoryCode,
                VERSION_ID);
    }

    private List<Long> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    }
}
