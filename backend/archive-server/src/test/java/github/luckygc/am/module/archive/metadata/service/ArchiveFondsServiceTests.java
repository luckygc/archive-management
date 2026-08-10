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
    @DisplayName("事件按全宗稳定编码读取")
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
        when(eventRepository.findByFondsCode("SYS-HD")).thenReturn(List.of(event));

        assertThat(service.listEvents(1L))
                .singleElement()
                .satisfies(
                        dto -> {
                            assertThat(dto.fondsCode()).isEqualTo("SYS-HD");
                            assertThat(dto.currentValue()).isEqualTo("HD-001");
                        });
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
