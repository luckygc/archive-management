package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemSource;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
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
@DisplayName("工作台摘要 MyBatis 集成")
class ArchiveWorkspaceMapperIntegrationTests extends PostgreSqlContainerTest {

    private static final String TABLE_NAME = "am_task10_item_data";

    @Autowired private ArchiveMapper archiveMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("PostgreSQL 聚合复用数据范围并排除主表和动态表逻辑删除记录")
    void summaryExecutesPostgreSqlFiltersAgainstVisibleItems() {
        createDynamicTable();
        insertItem(9_100_001L, "F001", "DRAFT", false, false, false);
        insertItem(9_100_002L, "F002", "ARCHIVED", true, false, false);
        insertItem(9_100_003L, "F001", "DRAFT", true, false, true);
        insertItem(9_100_004L, "F001", "DRAFT", true, true, false);
        insertItem(9_100_005L, "F001", "ARCHIVED", false, false, false);
        insertElectronicFile(9_100_001L, 9_100_101L);
        insertElectronicFile(9_100_001L, 9_100_102L);

        Map<String, Object> allData =
                archiveMapper.summarizeDynamicItems(
                        new ArchiveDynamicItemSource(TABLE_NAME, false), criteria(List.of()));
        Map<String, Object> fondsScoped =
                archiveMapper.summarizeDynamicItems(
                        new ArchiveDynamicItemSource(TABLE_NAME, false),
                        criteria(
                                List.of(
                                        new ArchiveDataScopeSqlGroup(
                                                List.of("F001"),
                                                List.of(),
                                                List.of(),
                                                List.of()))));

        assertCounts(allData, 3, 1, 1, 2);
        assertCounts(fondsScoped, 2, 1, 0, 2);
    }

    private ArchiveDynamicItemCriteria criteria(List<ArchiveDataScopeSqlGroup> groups) {
        return new ArchiveDynamicItemCriteria(null, null, groups, List.of(), List.of(), null);
    }

    private void createDynamicTable() {
        jdbcTemplate.execute(
                "create table "
                        + TABLE_NAME
                        + " (id bigint primary key, deleted_flag boolean not null default false)");
    }

    private void insertItem(
            long id,
            String fondsCode,
            String electronicStatus,
            boolean locked,
            boolean itemDeleted,
            boolean dynamicDeleted) {
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "electronic_status, archive_year, locked_flag, deleted_flag) "
                        + "values (?, ?, 'Task 10 全宗', 'TASK10', 'Task 10 分类', ?, ?, 2026, ?, ?)",
                id,
                fondsCode,
                "TASK10-" + id,
                electronicStatus,
                locked,
                itemDeleted);
        jdbcTemplate.update(
                "insert into " + TABLE_NAME + " (id, deleted_flag) values (?, ?)",
                id,
                dynamicDeleted);
    }

    private void insertElectronicFile(long archiveItemId, long storageObjectId) {
        jdbcTemplate.update(
                "insert into am_storage_object "
                        + "(id, bucket_name, object_key, original_filename, file_size) "
                        + "values (?, 'task10', ?, ?, 1)",
                storageObjectId,
                "task10/" + storageObjectId,
                storageObjectId + ".txt");
        jdbcTemplate.update(
                "insert into am_archive_item_electronic_file "
                        + "(archive_item_id, storage_object_id, usage_type) values (?, ?, 'DEFAULT')",
                archiveItemId,
                storageObjectId);
    }

    private void assertCounts(
            Map<String, Object> row,
            long archiveItemCount,
            long draftCount,
            long lockedCount,
            long electronicFileCount) {
        assertThat(number(row, "archive_item_count")).isEqualTo(archiveItemCount);
        assertThat(number(row, "draft_count")).isEqualTo(draftCount);
        assertThat(number(row, "locked_count")).isEqualTo(lockedCount);
        assertThat(number(row, "electronic_file_count")).isEqualTo(electronicFileCount);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
