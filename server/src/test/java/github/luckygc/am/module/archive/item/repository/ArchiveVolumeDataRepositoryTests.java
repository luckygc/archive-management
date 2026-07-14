package github.luckygc.am.module.archive.item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.item.ArchiveVolume;
import github.luckygc.am.module.archive.item._ArchiveVolume;
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
@DisplayName("案卷 Jakarta Data Repository")
class ArchiveVolumeDataRepositoryTests extends PostgreSqlContainerTest {

    private static final LocalDateTime SAME_CREATED_AT = LocalDateTime.of(2026, 7, 15, 10, 0);

    @Autowired private ArchiveVolumeDataRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("相同创建时间按 ID 稳定翻到下一页并返回上一页")
    void cursorPagingUsesIdAsStableTieBreakerInBothDirections() {
        insertVolume(9_150_001L, "V-001");
        insertVolume(9_150_002L, "V-002");
        insertVolume(9_150_003L, "V-003");
        Order<ArchiveVolume> order =
                Order.by(_ArchiveVolume.createdAt.desc(), _ArchiveVolume.id.desc());

        CursoredPage<ArchiveVolume> first =
                repository.find(
                        _ArchiveVolume.fondsCode.equalTo("TASK5"), PageRequest.ofSize(2), order);
        CursoredPage<ArchiveVolume> next =
                repository.find(
                        _ArchiveVolume.fondsCode.equalTo("TASK5"), first.nextPageRequest(), order);
        CursoredPage<ArchiveVolume> previous =
                repository.find(
                        _ArchiveVolume.fondsCode.equalTo("TASK5"),
                        next.previousPageRequest(),
                        order);

        assertThat(first.content())
                .extracting(ArchiveVolume::getId)
                .containsExactly(9_150_003L, 9_150_002L);
        assertThat(first.hasNext()).isTrue();
        assertThat(next.content()).extracting(ArchiveVolume::getId).containsExactly(9_150_001L);
        assertThat(next.hasPrevious()).isTrue();
        assertThat(previous.content())
                .extracting(ArchiveVolume::getId)
                .containsExactly(9_150_003L, 9_150_002L);
    }

    @Test
    @Transactional
    @DisplayName("Repository 接受 unrestricted 条件并保持稳定排序")
    void unrestrictedQueryStillUsesCallerOrder() {
        insertVolume(9_150_011L, "V-011");
        insertVolume(9_150_012L, "V-012");

        CursoredPage<ArchiveVolume> page =
                repository.find(
                        Restrict.all(
                                Restrict.unrestricted(), _ArchiveVolume.fondsCode.equalTo("TASK5")),
                        PageRequest.ofSize(10),
                        Order.by(_ArchiveVolume.createdAt.desc(), _ArchiveVolume.id.desc()));

        assertThat(page.content())
                .extracting(ArchiveVolume::getId)
                .containsExactly(9_150_012L, 9_150_011L);
    }

    private void insertVolume(long id, String archiveNo) {
        jdbcTemplate.update(
                "insert into am_archive_volume "
                        + "(id, fonds_code, fonds_name, category_code, category_name, archive_no, "
                        + "electronic_status, archive_year, created_at, updated_at) "
                        + "values (?, 'TASK5', '任务五全宗', 'TASK5_CATEGORY', '任务五分类', ?, "
                        + "'DRAFT', 2026, ?, ?)",
                id,
                archiveNo,
                SAME_CREATED_AT,
                SAME_CREATED_AT);
    }
}
