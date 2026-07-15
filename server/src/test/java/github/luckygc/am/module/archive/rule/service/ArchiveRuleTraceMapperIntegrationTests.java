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
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveRuleMapper;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria.ArchiveRuleTracePageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveRuleTraceSearchCriteria.ArchiveRuleTraceTargetScope;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
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
@DisplayName("规则追踪 MyBatis PostgreSQL 集成")
class ArchiveRuleTraceMapperIntegrationTests extends PostgreSqlContainerTest {

    private static final long SCHEME_ID = 9_500_000L;
    private static final long SCHEME_VERSION_ID = 9_500_001L;
    private static final String DYNAMIC_TABLE = "am_task5_rule_trace_item";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 15, 10, 0);

    @Autowired private ArchiveRuleMapper ruleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme (id, scheme_code, scheme_name) values (?, ?, ?)",
                SCHEME_ID,
                "task5-scheme",
                "任务五方案");
        jdbcTemplate.update(
                "insert into am_archive_governance_scheme_version (id, scheme_id, version_code) values (?, ?, ?)",
                SCHEME_VERSION_ID,
                SCHEME_ID,
                "v1");
    }

    @Test
    @DisplayName("SQL 在 limit 前过滤 501 条较新的不可见追踪")
    void visibilityIsAppliedBeforeLimit() {
        jdbcTemplate.update(
                "insert into am_archive_rule_trace "
                        + "(id, scheme_version_id, trigger_code, object_type_code, created_by, created_at) "
                        + "select 9510000 + n, ?, 'RUN', 'PROCESS', 8, ? + n * interval '1 second' "
                        + "from generate_series(1, 501) n",
                SCHEME_VERSION_ID,
                BASE_TIME);
        insertTrace(9_509_999L, "PROCESS", null, 7L, BASE_TIME.minusDays(1));

        List<Map<String, Object>> rows =
                ruleMapper.listRuleTraces(criteria(false, 7L, List.of(), List.of(), page(101)));

        assertThat(ids(rows)).containsExactly(9_509_999L);
    }

    @Test
    @DisplayName("相同创建时间使用 ID 稳定翻页且无重复遗漏")
    void stableCursorUsesCreatedAtAndId() {
        insertTrace(9_520_001L, "PROCESS", null, 7L, BASE_TIME);
        insertTrace(9_520_002L, "PROCESS", null, 7L, BASE_TIME);
        insertTrace(9_520_003L, "PROCESS", null, 7L, BASE_TIME);

        List<Map<String, Object>> first =
                ruleMapper.listRuleTraces(criteria(true, 7L, List.of(), List.of(), page(3)));
        List<Map<String, Object>> second =
                ruleMapper.listRuleTraces(
                        criteria(
                                true,
                                7L,
                                List.of(),
                                List.of(),
                                new ArchiveRuleTracePageWindow(false, BASE_TIME, 9_520_002L, 3)));

        assertThat(ids(first).subList(0, 2)).containsExactly(9_520_003L, 9_520_002L);
        assertThat(ids(second)).containsExactly(9_520_001L);
    }

    @Test
    @DisplayName("档案条目按固定全宗范围授权")
    void itemTraceUsesFondsScope() {
        insertItem(9_530_001L, "F001", "DOC");
        insertItem(9_530_002L, "F002", "DOC");
        insertTrace(9_531_001L, "ARCHIVE_ITEM", 9_530_001L, 8L, BASE_TIME.plusSeconds(2));
        insertTrace(9_531_002L, "ARCHIVE_ITEM", 9_530_002L, 8L, BASE_TIME.plusSeconds(1));
        ArchiveRuleTraceTargetScope scope = scope("DOC", DYNAMIC_TABLE, fondsGroup("F001"));

        List<Map<String, Object>> rows =
                ruleMapper.listRuleTraces(criteria(false, 7L, List.of(scope), List.of(), page(10)));

        assertThat(ids(rows)).containsExactly(9_531_001L);
    }

    @Test
    @DisplayName("空范围组授权该分类全部条目")
    void emptyGroupMeansAllItemsInCategory() {
        insertItem(9_535_001L, "F001", "DOC");
        insertItem(9_535_002L, "F002", "OTHER");
        insertTrace(9_536_001L, "ARCHIVE_ITEM", 9_535_001L, 8L, BASE_TIME.plusSeconds(2));
        insertTrace(9_536_002L, "ARCHIVE_ITEM", 9_535_002L, 8L, BASE_TIME.plusSeconds(1));
        ArchiveDataScopeSqlGroup allGroup =
                new ArchiveDataScopeSqlGroup(List.of(), List.of(), List.of(), List.of());

        List<Map<String, Object>> rows =
                ruleMapper.listRuleTraces(
                        criteria(
                                false,
                                7L,
                                List.of(scope("DOC", DYNAMIC_TABLE, allGroup)),
                                List.of(),
                                page(10)));

        assertThat(ids(rows)).containsExactly(9_536_001L);
    }

    @Test
    @DisplayName("档案条目动态字段条件在动态表中过滤")
    void itemTraceUsesDynamicCondition() {
        createDynamicTable();
        insertItem(9_540_001L, "F001", "DOC");
        insertItem(9_540_002L, "F001", "DOC");
        jdbcTemplate.update(
                "insert into " + DYNAMIC_TABLE + " (id, status) values (?, ?), (?, ?)",
                9_540_001L,
                "READY",
                9_540_002L,
                "DRAFT");
        insertTrace(9_541_001L, "ARCHIVE_ITEM", 9_540_001L, 8L, BASE_TIME.plusSeconds(2));
        insertTrace(9_541_002L, "ARCHIVE_ITEM", 9_540_002L, 8L, BASE_TIME.plusSeconds(1));
        ArchiveDataScopeSqlGroup group =
                new ArchiveDataScopeSqlGroup(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new ArchiveSqlCondition(
                                        "status", ArchiveItemQueryOperator.EQ, "READY")));

        List<Map<String, Object>> rows =
                ruleMapper.listRuleTraces(
                        criteria(
                                false,
                                7L,
                                List.of(scope("DOC", DYNAMIC_TABLE, group)),
                                List.of(),
                                page(10)));

        assertThat(ids(rows)).containsExactly(9_541_001L);
    }

    @Test
    @DisplayName("案卷不接受仅含动态条件的数据范围组")
    void volumeTraceDoesNotUseDynamicConditionGroup() {
        createDynamicTable();
        insertVolume(9_550_001L, "F001", "DOC");
        insertTrace(9_551_001L, "ARCHIVE_VOLUME", 9_550_001L, 7L, BASE_TIME);
        ArchiveDataScopeSqlGroup dynamicGroup =
                new ArchiveDataScopeSqlGroup(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new ArchiveSqlCondition(
                                        "status", ArchiveItemQueryOperator.EQ, "READY")));

        List<Map<String, Object>> rows =
                ruleMapper.listRuleTraces(
                        criteria(
                                false,
                                7L,
                                List.of(scope("DOC", DYNAMIC_TABLE, dynamicGroup)),
                                List.of(),
                                page(10)));

        assertThat(rows).isEmpty();
    }

    private ArchiveRuleTraceSearchCriteria criteria(
            boolean allData,
            long userId,
            List<ArchiveRuleTraceTargetScope> itemScopes,
            List<ArchiveRuleTraceTargetScope> volumeScopes,
            ArchiveRuleTracePageWindow page) {
        return new ArchiveRuleTraceSearchCriteria(
                null, null, null, null, null, allData, userId, itemScopes, volumeScopes, page);
    }

    private ArchiveRuleTracePageWindow page(int rowLimit) {
        return new ArchiveRuleTracePageWindow(false, null, null, rowLimit);
    }

    private ArchiveRuleTraceTargetScope scope(
            String categoryCode, String tableName, ArchiveDataScopeSqlGroup group) {
        return new ArchiveRuleTraceTargetScope(categoryCode, tableName, List.of(group));
    }

    private ArchiveDataScopeSqlGroup fondsGroup(String fondsCode) {
        return new ArchiveDataScopeSqlGroup(List.of(fondsCode), List.of(), List.of(), List.of());
    }

    private void createDynamicTable() {
        jdbcTemplate.execute(
                "create table "
                        + DYNAMIC_TABLE
                        + " (id bigint primary key, status varchar(30), deleted_flag boolean not null default false)");
    }

    private void insertTrace(
            long id, String objectType, Long objectId, long createdBy, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "insert into am_archive_rule_trace "
                        + "(id, scheme_version_id, trigger_code, object_type_code, object_id, created_by, created_at) "
                        + "values (?, ?, 'RUN', ?, ?, ?, ?)",
                id,
                SCHEME_VERSION_ID,
                objectType,
                objectId,
                createdBy,
                createdAt);
    }

    private void insertItem(long id, String fondsCode, String categoryCode) {
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, electronic_status, archive_year) "
                        + "values (?, ?, '全宗', ?, '分类', 'NONE', 2026)",
                id,
                fondsCode,
                categoryCode);
    }

    private void insertVolume(long id, String fondsCode, String categoryCode) {
        jdbcTemplate.update(
                "insert into am_archive_volume "
                        + "(id, fonds_code, fonds_name, category_code, category_name, electronic_status, archive_year) "
                        + "values (?, ?, '全宗', ?, '分类', 'NONE', 2026)",
                id,
                fondsCode,
                categoryCode);
    }

    private List<Long> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    }
}
