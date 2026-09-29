package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveFondsEvent;
import github.luckygc.am.module.archive.metadata.ArchiveFondsEventType;
import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsEventDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.AssignArchiveFondsNumberRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CloseArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CreateArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ReopenArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveFondsRequest;

@DisplayName("全宗生命周期服务")
class ArchiveFondsServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 9, 30);
    private final ArchiveFondsDataRepository fondsRepository =
            mock(ArchiveFondsDataRepository.class);
    private final ArchiveFondsEventDataRepository eventRepository =
            mock(ArchiveFondsEventDataRepository.class);
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-08-11T01:30:00Z"), ZoneId.of("Asia/Shanghai"));
    private ArchiveFondsService service;

    @BeforeEach
    void setUp() {
        service = new ArchiveFondsService(fondsRepository, eventRepository, clock);
        when(fondsRepository.insert(any()))
                .thenAnswer(invocation -> persist(invocation.getArgument(0)));
        when(fondsRepository.update(any()))
                .thenAnswer(invocation -> persist(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("创建时只登记稳定系统编码，不混入业务全宗号")
    void createKeepsSystemCodeSeparateFromBusinessNumber() {
        var result =
                service.createFonds(
                        new CreateArchiveFondsRequest(
                                " SYS-HD ", " 华东公司 ", LocalDate.of(2000, 1, 1), null, "沿革", 10),
                        9L);

        assertThat(result.fondsCode()).isEqualTo("SYS-HD");
        assertThat(result.fondsNo()).isNull();
        assertThat(result.status()).isEqualTo(ArchiveFondsStatus.ACTIVE);
        verify(eventRepository, never()).insert(any());
    }

    @Test
    @DisplayName("局部更新保留缺失字段，只清空显式删除的可选字段")
    void updateFondsPreservesMissingFieldsAndRemovesRequestedFields() {
        ArchiveFonds fonds = fonds(ArchiveFondsStatus.ACTIVE);
        fonds.setStartDate(LocalDate.of(2000, 1, 1));
        fonds.setEndDate(LocalDate.of(2020, 1, 1));
        fonds.setHistoryNote("原有沿革");
        when(fondsRepository.findById(1L)).thenReturn(Optional.of(fonds));

        var updated =
                service.updateFonds(
                        1L,
                        new UpdateArchiveFondsRequest(
                                null, false, null, true, null, true, null, null),
                        9L);

        assertThat(updated.fondsName()).isEqualTo("华东公司");
        assertThat(updated.startDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(updated.endDate()).isNull();
        assertThat(updated.historyNote()).isNull();
        verify(fondsRepository).update(fonds);
    }

    @Test
    @DisplayName("空补丁不写数据库，最终日期不合法时整体拒绝")
    void updateFondsAvoidsNoopAndValidatesMergedDates() {
        ArchiveFonds fonds = fonds(ArchiveFondsStatus.ACTIVE);
        fonds.setStartDate(LocalDate.of(2000, 1, 1));
        fonds.setEndDate(LocalDate.of(2020, 1, 1));
        when(fondsRepository.findById(1L)).thenReturn(Optional.of(fonds));

        var unchanged =
                service.updateFonds(
                        1L,
                        new UpdateArchiveFondsRequest(
                                null, false, null, false, null, false, null, null),
                        9L);

        assertThat(unchanged.endDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThatThrownBy(
                        () ->
                                service.updateFonds(
                                        1L,
                                        new UpdateArchiveFondsRequest(
                                                null,
                                                true,
                                                LocalDate.of(2021, 1, 1),
                                                false,
                                                null,
                                                false,
                                                null,
                                                null),
                                        9L))
                .isInstanceOf(BadRequestException.class);
        verify(fondsRepository, never()).update(any());
        assertThat(fonds.getStartDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("分配全宗号只允许一次并追加编号事件")
    void assignNumberIsOneTimeAndAppendsEvent() {
        ArchiveFonds fonds = fonds(ArchiveFondsStatus.ACTIVE);
        when(fondsRepository.findById(1L)).thenReturn(Optional.of(fonds));

        var result =
                service.assignNumber(
                        1L,
                        new AssignArchiveFondsNumberRequest(" HD-001 ", "省档案局", "完成登记", null),
                        9L);

        assertThat(result.fondsNo()).isEqualTo("HD-001");
        assertThat(result.numberAssignedAt()).isEqualTo(NOW);
        ArgumentCaptor<ArchiveFondsEvent> eventCaptor =
                ArgumentCaptor.forClass(ArchiveFondsEvent.class);
        verify(eventRepository).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType())
                .isEqualTo(ArchiveFondsEventType.NUMBER_ASSIGNED);
        assertThat(eventCaptor.getValue().getReason()).isEqualTo("完成登记");
        assertThat(eventCaptor.getValue().getOperatedBy()).isEqualTo(9L);

        assertThatThrownBy(
                        () ->
                                service.assignNumber(
                                        1L,
                                        new AssignArchiveFondsNumberRequest(
                                                "HD-002", null, "再次编号", null),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已有业务全宗号");
    }

    @Test
    @DisplayName("数据库拒绝重复全宗号时返回冲突且不写事件")
    void duplicateNumberReturnsConflictWithoutEvent() {
        when(fondsRepository.findById(1L))
                .thenReturn(Optional.of(fonds(ArchiveFondsStatus.ACTIVE)));
        doThrow(new DataIntegrityViolationException("duplicate fonds_no"))
                .when(fondsRepository)
                .update(any());

        assertThatThrownBy(
                        () ->
                                service.assignNumber(
                                        1L,
                                        new AssignArchiveFondsNumberRequest(
                                                "HD-001", null, "完成登记", null),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("全宗号已被其他全宗使用");
        verify(eventRepository, never()).insert(any());
    }

    @Test
    @DisplayName("封闭与重新开放均通过显式状态动作并记录原因")
    void closeAndReopenUseLifecycleActions() {
        ArchiveFonds fonds = fonds(ArchiveFondsStatus.ACTIVE);
        when(fondsRepository.findById(1L)).thenReturn(Optional.of(fonds));

        var closed = service.closeFonds(1L, new CloseArchiveFondsRequest("机构撤并", null), 9L);

        assertThat(closed.status()).isEqualTo(ArchiveFondsStatus.CLOSED);
        assertThat(closed.closedAt()).isEqualTo(NOW);
        assertThat(closed.closureReason()).isEqualTo("机构撤并");

        var reopened = service.reopenFonds(1L, new ReopenArchiveFondsRequest("恢复独立立档", null), 9L);

        assertThat(reopened.status()).isEqualTo(ArchiveFondsStatus.ACTIVE);
        assertThat(reopened.closedAt()).isNull();
        ArgumentCaptor<ArchiveFondsEvent> eventCaptor =
                ArgumentCaptor.forClass(ArchiveFondsEvent.class);
        verify(eventRepository, org.mockito.Mockito.times(2)).insert(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(ArchiveFondsEvent::getEventType)
                .containsExactly(ArchiveFondsEventType.CLOSED, ArchiveFondsEventType.REOPENED);
    }

    @Test
    @DisplayName("动作生效时间不得晚于当前时间")
    void lifecycleEffectiveTimeCannotBeInFuture() {
        when(fondsRepository.findById(1L))
                .thenReturn(Optional.of(fonds(ArchiveFondsStatus.ACTIVE)));

        assertThatThrownBy(
                        () ->
                                service.closeFonds(
                                        1L,
                                        new CloseArchiveFondsRequest("机构撤并", NOW.plusSeconds(1)),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("生效时间不能晚于当前时间");
    }

    @Test
    @DisplayName("事件按全宗稳定编码分页读取且不执行总数查询")
    void eventsAreReadByStableFondsCode() {
        ArchiveFonds fonds = fonds(ArchiveFondsStatus.ACTIVE);
        ArchiveFondsEvent event = new ArchiveFondsEvent();
        event.setId(11L);
        event.setFondsCode("SYS-HD");
        event.setEventType(ArchiveFondsEventType.NUMBER_ASSIGNED);
        event.setCurrentValue("HD-001");
        event.setReason("完成登记");
        event.setEffectiveAt(NOW);
        event.setCreatedAt(NOW);
        when(fondsRepository.findById(1L)).thenReturn(Optional.of(fonds));
        @SuppressWarnings("unchecked")
        CursoredPage<ArchiveFondsEvent> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(List.of(event));
        when(eventRepository.findByFondsCode(org.mockito.ArgumentMatchers.eq("SYS-HD"), any()))
                .thenReturn(page);

        assertThat(service.listEvents(1L, PageRequest.ofSize(2).withTotal()).items())
                .singleElement()
                .satisfies(
                        dto -> {
                            assertThat(dto.fondsCode()).isEqualTo("SYS-HD");
                            assertThat(dto.currentValue()).isEqualTo("HD-001");
                        });
        ArgumentCaptor<PageRequest> capturedPage = ArgumentCaptor.forClass(PageRequest.class);
        verify(eventRepository)
                .findByFondsCode(org.mockito.ArgumentMatchers.eq("SYS-HD"), capturedPage.capture());
        assertThat(capturedPage.getValue().size()).isEqualTo(2);
        assertThat(capturedPage.getValue().requestTotal()).isFalse();
    }

    private ArchiveFonds fonds(ArchiveFondsStatus status) {
        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setId(1L);
        fonds.setFondsCode("SYS-HD");
        fonds.setFondsName("华东公司");
        fonds.setStatus(status);
        fonds.setSortOrder(10);
        fonds.setCreatedAt(NOW.minusDays(1));
        fonds.setUpdatedAt(NOW.minusDays(1));
        if (status == ArchiveFondsStatus.CLOSED) {
            fonds.setClosedAt(NOW.minusHours(1));
            fonds.setClosureReason("机构撤并");
        }
        return fonds;
    }

    private ArchiveFonds persist(ArchiveFonds fonds) {
        if (fonds.getId() == null) fonds.setId(1L);
        if (fonds.getCreatedAt() == null) fonds.setCreatedAt(NOW);
        fonds.setUpdatedAt(NOW);
        return fonds;
    }
}
