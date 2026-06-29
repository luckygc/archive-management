package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.api.OffsetPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditQueryService.ArchiveItemAuditQuery;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditQueryService.ArchiveItemAuditResponse;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@DisplayName("档案条目操作审计查询")
class ArchiveItemAuditQueryServiceTests {

    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemAuditQueryService auditQueryService =
            new ArchiveItemAuditQueryService(archiveMapper);

    @Test
    @DisplayName("按档案、分类、全宗、操作类型和操作时间分页查询审计")
    void listAuditsShouldApplyFiltersAndMapPage() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime operatedAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        when(archiveMapper.countArchiveItemAudits(10L, "F001", "contract", "UPDATE", start, end))
                .thenReturn(1L);
        when(archiveMapper.listArchiveItemAudits(
                        10L, "F001", "contract", "UPDATE", start, end, 20, 40L))
                .thenReturn(
                        List.of(
                                Map.ofEntries(
                                        Map.entry("id", 99L),
                                        Map.entry("source_table_name", "am_archive_item"),
                                        Map.entry("source_item_id", 10L),
                                        Map.entry("archive_item_id", 10L),
                                        Map.entry("fonds_code", "F001"),
                                        Map.entry("category_code", "contract"),
                                        Map.entry("operation_type", "UPDATE"),
                                        Map.entry("operation_reason", "修正题名"),
                                        Map.entry("operated_by", 9L),
                                        Map.entry("operated_at", operatedAt))));

        OffsetPageResponse<ArchiveItemAuditResponse> page =
                auditQueryService.listAudits(
                        new ArchiveItemAuditQuery(
                                10L, " F001 ", " contract ", " UPDATE ", start, end, 20, 40L));

        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.offset()).isEqualTo(40L);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items())
                .containsExactly(
                        new ArchiveItemAuditResponse(
                                99L,
                                "am_archive_item",
                                10L,
                                10L,
                                "F001",
                                "contract",
                                "UPDATE",
                                "修正题名",
                                9L,
                                operatedAt));
        verify(archiveMapper)
                .listArchiveItemAudits(10L, "F001", "contract", "UPDATE", start, end, 20, 40L);
    }

    @Test
    @DisplayName("拒绝负数 offset")
    void listAuditsShouldRejectNegativeOffset() {
        assertThatThrownBy(
                        () ->
                                auditQueryService.listAudits(
                                        new ArchiveItemAuditQuery(
                                                null, null, null, null, null, null, 100, -1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("分页参数不合法");
    }

    @Test
    @DisplayName("拒绝超过上限的 page size")
    void listAuditsShouldRejectLimitOverMaximum() {
        assertThatThrownBy(
                        () ->
                                auditQueryService.listAudits(
                                        new ArchiveItemAuditQuery(
                                                null, null, null, null, null, null, 2000, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("分页参数不合法");
    }
}
