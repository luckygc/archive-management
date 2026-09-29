package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemPageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemSource;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;

class ArchiveItemOverviewPaginationTests {

    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemCursorPageAssembler assembler =
            new ArchiveItemCursorPageAssembler(archiveMapper);

    @Test
    void limitsTheDatabaseQueryAndReturnsNextPageFromStableKeys() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 29, 8, 0);
        when(archiveMapper.listItemOverview(eq("F001"), any()))
                .thenReturn(List.of(row(3L, createdAt), row(2L, createdAt), row(1L, createdAt)));
        PageRequest request = PageRequest.ofSize(2).withTotal();
        when(archiveMapper.countItemOverview("F001")).thenReturn(5L);

        var page = assembler.queryOverviewPage(request, "F001", null);

        assertThat(page.content()).extracting(row -> row.get("id")).containsExactly(3L, 2L);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.cursor(1).elements()).isEqualTo(List.of(createdAt, 2L));
        ArgumentCaptor<ArchiveDynamicItemPageWindow> window =
                ArgumentCaptor.forClass(ArchiveDynamicItemPageWindow.class);
        verify(archiveMapper).listItemOverview(eq("F001"), window.capture());
        assertThat(window.getValue().limit()).isEqualTo(3);
        assertThat(window.getValue().orders())
                .extracting(order -> order.expression())
                .containsExactly("i.created_at", "i.id");
    }

    @Test
    void previousPageRestoresDisplayOrderAndNavigationDirections() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 29, 8, 0);
        PageRequest request =
                PageRequest.ofSize(2).beforeCursor(PageRequest.Cursor.forKey(createdAt, 2L));
        when(archiveMapper.listItemOverview(eq(null), any()))
                .thenReturn(List.of(row(3L, createdAt), row(4L, createdAt), row(5L, createdAt)));

        var page = assembler.queryOverviewPage(request, null, request.cursor().orElseThrow());

        assertThat(page.content()).extracting(row -> row.get("id")).containsExactly(4L, 3L);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
        ArgumentCaptor<ArchiveDynamicItemPageWindow> window =
                ArgumentCaptor.forClass(ArchiveDynamicItemPageWindow.class);
        verify(archiveMapper).listItemOverview(eq(null), window.capture());
        assertThat(window.getValue().orders())
                .allSatisfy(order -> assertThat(order.directionName()).isEqualTo("ASC"));
    }

    @Test
    void dynamicSearchPreviousPageUsesTheSameNavigationDirections() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 29, 8, 0);
        PageRequest request =
                PageRequest.ofSize(2).beforeCursor(PageRequest.Cursor.forKey(createdAt, 2L));
        when(archiveMapper.listDynamicItems(any(), any(), any(), any()))
                .thenReturn(List.of(row(3L, createdAt), row(4L, createdAt)));

        var page =
                assembler.queryDynamicItemPage(
                        request,
                        new ArchiveDynamicItemSource("am_archive_item_data_contract", false),
                        List.of(),
                        new ArchiveDynamicItemCriteria(
                                null, null, List.of(), List.of(), List.of(), null),
                        List.of(
                                new ArchiveSqlOrder("i.created_at", Direction.DESC),
                                new ArchiveSqlOrder("i.id", Direction.DESC)),
                        request.cursor().orElseThrow());

        assertThat(page.content()).extracting(row -> row.get("id")).containsExactly(4L, 3L);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isTrue();
    }

    private Map<String, Object> row(Long id, LocalDateTime createdAt) {
        return Map.of("id", id, "created_at", createdAt);
    }
}
