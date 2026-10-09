package github.luckygc.am.module.archive.item.repository;

import static github.luckygc.am.test.ArchiveTestFixtures.insertActiveFonds;
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
import github.luckygc.am.module.archive.item.ArchiveItem;
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
@DisplayName("档案条目 Jakarta Data Repository")
class ArchiveItemDataRepositoryTests extends PostgreSqlContainerTest {

    @Autowired private ArchiveItemDataRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("批量读取沿用单条读取的软删除和记录缺失语义")
    void batchFindExcludesDeletedAndMissingItems() {
        insertActiveFonds(jdbcTemplate, "BATCH_EXPORT", "批量导出测试全宗");
        insertItem(9_660_001L, false);
        insertItem(9_660_002L, false);
        insertItem(9_660_003L, true);

        List<ArchiveItem> result =
                repository.findByIdIn(
                        List.of(9_660_002L, 9_660_003L, 9_660_099L, 9_660_001L, 9_660_001L));

        assertThat(result)
                .extracting(ArchiveItem::getId)
                .containsExactlyInAnyOrder(9_660_001L, 9_660_002L);
        assertThat(repository.findById(9_660_003L)).isEmpty();
        assertThat(repository.findById(9_660_099L)).isEmpty();
    }

    private void insertItem(long id, boolean deleted) {
        jdbcTemplate.update(
                "insert into am_archive_item "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "archive_year, deleted_flag) "
                        + "values (?, 'BATCH_EXPORT', '批量导出测试全宗', 'BATCH_CATEGORY', '批量分类', "
                        + "?, 2026, ?)",
                id,
                "BATCH-" + id,
                deleted);
    }
}
