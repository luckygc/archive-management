package github.luckygc.am.module.archive.physical.repository;

import static github.luckygc.am.test.ArchiveTestFixtures.insertActiveFonds;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.physical.ArchivePhysicalLocationHistory;
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
class ArchivePhysicalLocationHistoryDataRepositoryTests extends PostgreSqlContainerTest {

    private static final LocalDateTime OPERATED_AT = LocalDateTime.of(2026, 9, 29, 8, 0);

    @Autowired private ArchivePhysicalLocationHistoryDataRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void pagesEqualTimestampsByIdInBothDirections() {
        insertActiveFonds(jdbcTemplate, "HIST25", "历史测试全宗");
        jdbcTemplate.update(
                "insert into am_archive_volume "
                        + "(id, fonds_code, fonds_name, category_code, category_name, "
                        + "archive_no, archive_year, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, 2026, ?, ?)",
                9_250_001L,
                "HIST25",
                "历史测试全宗",
                "HIST25_CATEGORY",
                "历史测试分类",
                "HIST25-001",
                OPERATED_AT,
                OPERATED_AT);
        jdbcTemplate.update(
                "insert into am_archive_warehouse (id, warehouse_code, warehouse_name) "
                        + "values (?, ?, ?)",
                9_250_011L,
                "HIST25_WAREHOUSE",
                "历史测试库房");
        jdbcTemplate.update(
                "insert into am_archive_storage_location "
                        + "(id, warehouse_id, location_code, location_name, location_type) "
                        + "values (?, ?, ?, ?, ?)",
                9_250_021L,
                9_250_011L,
                "HIST25_LOCATION",
                "历史测试位置",
                "SHELF");
        jdbcTemplate.update(
                "insert into am_archive_physical_object (id, archive_volume_id) values (?, ?)",
                9_250_031L,
                9_250_001L);
        for (long id : new long[] {9_250_041L, 9_250_042L, 9_250_043L}) {
            jdbcTemplate.update(
                    "insert into am_archive_physical_location_history "
                            + "(id, physical_object_id, to_location_id, operated_at) "
                            + "values (?, ?, ?, ?)",
                    id,
                    9_250_031L,
                    9_250_021L,
                    OPERATED_AT);
        }

        CursoredPage<ArchivePhysicalLocationHistory> first =
                repository.list(9_250_031L, PageRequest.ofSize(2).withoutTotal());
        CursoredPage<ArchivePhysicalLocationHistory> next =
                repository.list(9_250_031L, first.nextPageRequest());
        CursoredPage<ArchivePhysicalLocationHistory> previous =
                repository.list(9_250_031L, next.previousPageRequest());

        assertThat(first.content())
                .extracting(ArchivePhysicalLocationHistory::getId)
                .containsExactly(9_250_043L, 9_250_042L);
        assertThat(first.hasNext()).isTrue();
        assertThat(next.content())
                .extracting(ArchivePhysicalLocationHistory::getId)
                .containsExactly(9_250_041L);
        assertThat(next.hasPrevious()).isTrue();
        assertThat(previous.content())
                .extracting(ArchivePhysicalLocationHistory::getId)
                .containsExactly(9_250_043L, 9_250_042L);
    }
}
