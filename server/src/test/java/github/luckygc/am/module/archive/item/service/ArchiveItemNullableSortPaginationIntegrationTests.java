package github.luckygc.am.module.archive.item.service;

import static github.luckygc.am.test.ArchiveTestFixtures.insertActiveFonds;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemSource;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false", "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false", "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
class ArchiveItemNullableSortPaginationIntegrationTests extends PostgreSqlContainerTest {
    private static final String TABLE = "am_nullable_sort_data";
    private static final CursorPageTokenContext CONTEXT =
            new CursorPageTokenContext("nullable-sort-http");
    @Autowired private ArchiveItemCursorPageAssembler assembler;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(Direction.class)
    @Transactional
    void hiddenNullableMultiColumnSortTraversesBothDirectionsWithoutDuplicates(
            Direction direction) {
        insertActiveFonds(jdbc, "FNULL", "空值分页全宗");
        jdbc.execute(
                "create table "
                        + TABLE
                        + " (id bigint primary key, deleted_flag boolean not null default false, hidden_score integer, hidden_note text)");
        insert(9_200_001L, null, null);
        insert(9_200_002L, null, "甲");
        insert(9_200_003L, 1, null);
        insert(9_200_004L, 1, "甲");
        insert(9_200_005L, 1, "甲");
        insert(9_200_006L, 2, null);
        var expected =
                direction == Direction.ASC
                        ? List.of(
                                9_200_003L,
                                9_200_005L,
                                9_200_004L,
                                9_200_006L,
                                9_200_001L,
                                9_200_002L)
                        : List.of(
                                9_200_001L,
                                9_200_002L,
                                9_200_006L,
                                9_200_003L,
                                9_200_005L,
                                9_200_004L);
        var orders =
                List.of(
                        new ArchiveSqlOrder("d.hidden_score", direction),
                        new ArchiveSqlOrder("d.hidden_note", Direction.DESC),
                        new ArchiveSqlOrder("i.id", Direction.DESC));
        var pages = new ArrayList<List<Long>>();
        var page = query(PageRequest.ofSize(2).withoutTotal(), orders);
        while (true) {
            assertThat(page.content())
                    .allSatisfy(
                            row ->
                                    assertThat(row)
                                            .doesNotContainKeys("hidden_score", "hidden_note"));
            pages.add(ids(page));
            if (!page.hasNext()) break;
            assertThat(pages).hasSizeLessThan(4);
            page =
                    query(
                            CursorPageTokenCodec.pageRequest(
                                    2, response(page).next(), false, CONTEXT),
                            orders);
        }
        assertThat(pages.stream().flatMap(List::stream).toList())
                .containsExactlyElementsOf(expected);
        for (int index = pages.size() - 2; index >= 0; index--) {
            assertThat(page.hasPrevious()).isTrue();
            page =
                    query(
                            CursorPageTokenCodec.pageRequest(
                                    2, response(page).prev(), false, CONTEXT),
                            orders);
            assertThat(ids(page)).isEqualTo(pages.get(index));
        }
        assertThat(page.hasPrevious()).isFalse();
    }

    private CursorPageResponse<Map<String, @Nullable Object>> response(
            CursoredPage<Map<String, @Nullable Object>> page) {
        var response =
                CursorPageResponse.from(page, page.pageRequest(), row -> row)
                        .encodeCursorTokens(CONTEXT);
        assertThat(CursorPageTokenCodec.validate(response.self(), 2, CONTEXT).values())
                .isEqualTo(page.cursor(0).elements());
        return response;
    }

    private CursoredPage<Map<String, @Nullable Object>> query(
            PageRequest request, List<ArchiveSqlOrder> orders) {
        return assembler.queryDynamicItemPage(
                request,
                new ArchiveDynamicItemSource(TABLE, false),
                List.of(),
                new ArchiveDynamicItemCriteria(
                        "FNULL", null, List.of(), List.of(), List.of(), null),
                orders,
                request.cursor().orElse(null));
    }

    private List<Long> ids(CursoredPage<Map<String, @Nullable Object>> page) {
        return page.content().stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    }

    private void insert(long id, @Nullable Integer score, @Nullable String note) {
        jdbc.update(
                "insert into am_archive_item (id, fonds_code, fonds_name, category_code, category_name, archive_no, archive_year, repository_id, locked_flag, deleted_flag) values (?, 'FNULL', '空值分页全宗', 'NULLSORT', '空值分页分类', ?, 2026, 2, false, false)",
                id,
                "NULLSORT-" + id);
        jdbc.update(
                "insert into " + TABLE + " (id, hidden_score, hidden_note) values (?, ?, ?)",
                id,
                score,
                note);
    }
}
