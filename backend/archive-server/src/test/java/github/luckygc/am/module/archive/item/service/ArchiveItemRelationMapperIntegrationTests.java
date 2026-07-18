package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveItemRelationCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveItemRelationCriteria.ArchiveItemRelationPageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveItemRelationCriteria.ArchiveItemRelationTargetScope;
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
@DisplayName("档案关系 MyBatis 集成")
class ArchiveItemRelationMapperIntegrationTests extends PostgreSqlContainerTest {

    @Autowired private ArchiveMapper archiveMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("仅分类范围生成的空条件组允许读取目标分类关系")
    void categoryOnlyEmptyGroupAllowsRelatedItem() {
        long sourceItemId = 9_160_001L;
        long targetItemId = 9_160_002L;
        insertItem(sourceItemId, "SOURCE", "SOURCE-001");
        insertItem(targetItemId, "TARGET", "TARGET-001");
        Long relationId = archiveMapper.insertItemRelation(sourceItemId, targetItemId);
        ArchiveItemRelationCriteria criteria =
                new ArchiveItemRelationCriteria(
                        false,
                        List.of(
                                new ArchiveItemRelationTargetScope(
                                        "TARGET",
                                        "unused_for_empty_group",
                                        List.of(
                                                new ArchiveDataScopeSqlGroup(
                                                        List.of(), List.of(), List.of(),
                                                        List.of())))),
                        true);

        var rows =
                archiveMapper.listItemRelations(
                        sourceItemId, criteria, new ArchiveItemRelationPageWindow(false, null, 10));

        assertThat(rows).extracting(row -> row.get("id")).containsExactly(relationId);
        assertThat(rows).extracting(row -> row.get("total")).containsExactly(1L);
    }

    @Test
    @Transactional
    @DisplayName("真正的空数据范围仍拒绝全部关系")
    void emptyFilterStillFailsClosed() {
        long sourceItemId = 9_160_011L;
        long targetItemId = 9_160_012L;
        insertItem(sourceItemId, "SOURCE", "SOURCE-011");
        insertItem(targetItemId, "TARGET", "TARGET-011");
        archiveMapper.insertItemRelation(sourceItemId, targetItemId);

        var rows =
                archiveMapper.listItemRelations(
                        sourceItemId,
                        new ArchiveItemRelationCriteria(false, List.of(), false),
                        new ArchiveItemRelationPageWindow(false, null, 10));

        assertThat(rows).isEmpty();
    }

    private void insertItem(long id, String categoryCode, String archiveNo) {
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "electronic_status, archive_year) values (?, 'TASK6', '任务六全宗', ?, "
                        + "'任务六分类', ?, 'DRAFT', 2026)",
                id,
                categoryCode,
                archiveNo);
    }
}
