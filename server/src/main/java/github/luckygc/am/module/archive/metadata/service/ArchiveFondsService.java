package github.luckygc.am.module.archive.metadata.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveFondsEvent;
import github.luckygc.am.module.archive.metadata.ArchiveFondsEventType;
import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsEventDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsEventDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.AssignArchiveFondsNumberRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CloseArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CreateArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ReopenArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveFondsRequest;

@Service
public class ArchiveFondsService {

    private final ArchiveFondsDataRepository fondsRepository;
    private final ArchiveFondsEventDataRepository eventRepository;
    private final Clock clock;

    public ArchiveFondsService(
            ArchiveFondsDataRepository fondsRepository,
            ArchiveFondsEventDataRepository eventRepository,
            Clock clock) {
        this.fondsRepository = fondsRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    public List<ArchiveFondsDto> listFonds(@Nullable ArchiveFondsStatus status) {
        List<ArchiveFonds> fonds =
                status == null ? fondsRepository.list() : fondsRepository.list(status);
        return fonds.stream().map(this::toDto).toList();
    }

    @Transactional
    public ArchiveFondsDto createFonds(CreateArchiveFondsRequest request, Long userId) {
        String fondsCode = requireText(request.fondsCode(), "系统全宗编码不能为空", "fondsCode", 100);
        String fondsName = requireText(request.fondsName(), "全宗名称不能为空", "fondsName", 255);
        validateDates(request.startDate(), request.endDate());

        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setFondsCode(fondsCode);
        fonds.setFondsName(fondsName);
        fonds.setStatus(ArchiveFondsStatus.ACTIVE);
        fonds.setStartDate(request.startDate());
        fonds.setEndDate(request.endDate());
        fonds.setHistoryNote(StringUtils.trimToNull(request.historyNote()));
        fonds.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        try {
            return toDto(fondsRepository.insert(fonds));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("系统全宗编码已存在", exception);
        }
    }

    @Transactional
    public ArchiveFondsDto updateFonds(Long id, UpdateArchiveFondsRequest request, Long userId) {
        ArchiveFonds fonds = loadFonds(id);
        String fondsName = requireText(request.fondsName(), "全宗名称不能为空", "fondsName", 255);
        validateDates(request.startDate(), request.endDate());

        fonds.setFondsName(fondsName);
        fonds.setStartDate(request.startDate());
        fonds.setEndDate(request.endDate());
        fonds.setHistoryNote(StringUtils.trimToNull(request.historyNote()));
        fonds.setSortOrder(
                request.sortOrder() == null ? fonds.getSortOrder() : request.sortOrder());
        return toDto(fondsRepository.update(fonds));
    }

    @Transactional
    public ArchiveFondsDto assignNumber(
            Long id, AssignArchiveFondsNumberRequest request, Long userId) {
        ArchiveFonds fonds = loadFonds(id);
        if (fonds.getFondsNo() != null) {
            throw conflict("该全宗已有业务全宗号");
        }
        String fondsNo = requireText(request.fondsNo(), "全宗号不能为空", "fondsNo", 100);
        String reason = requireReason(request.reason());
        LocalDateTime effectiveAt = resolveEffectiveAt(request.effectiveAt());

        fonds.setFondsNo(fondsNo);
        fonds.setNumberAssignedBy(optionalText(request.assignedBy(), "assignedBy", 255));
        fonds.setNumberAssignedAt(effectiveAt);
        try {
            ArchiveFonds updated = fondsRepository.update(fonds);
            insertEvent(
                    updated,
                    ArchiveFondsEventType.NUMBER_ASSIGNED,
                    null,
                    fondsNo,
                    reason,
                    effectiveAt,
                    userId);
            return toDto(updated);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("全宗号已被其他全宗使用", exception);
        }
    }

    @Transactional
    public ArchiveFondsDto closeFonds(Long id, CloseArchiveFondsRequest request, Long userId) {
        ArchiveFonds fonds = loadFonds(id);
        if (fonds.getStatus() == ArchiveFondsStatus.CLOSED) {
            throw conflict("全宗已经封闭");
        }
        String reason = requireReason(request.reason());
        LocalDateTime effectiveAt = resolveEffectiveAt(request.effectiveAt());

        fonds.setStatus(ArchiveFondsStatus.CLOSED);
        fonds.setClosedAt(effectiveAt);
        fonds.setClosureReason(reason);
        ArchiveFonds updated = fondsRepository.update(fonds);
        insertEvent(
                updated,
                ArchiveFondsEventType.CLOSED,
                ArchiveFondsStatus.ACTIVE.name(),
                ArchiveFondsStatus.CLOSED.name(),
                reason,
                effectiveAt,
                userId);
        return toDto(updated);
    }

    @Transactional
    public ArchiveFondsDto reopenFonds(Long id, ReopenArchiveFondsRequest request, Long userId) {
        ArchiveFonds fonds = loadFonds(id);
        if (fonds.getStatus() == ArchiveFondsStatus.ACTIVE) {
            throw conflict("全宗当前为有效状态");
        }
        String reason = requireReason(request.reason());
        LocalDateTime effectiveAt = resolveEffectiveAt(request.effectiveAt());

        fonds.setStatus(ArchiveFondsStatus.ACTIVE);
        fonds.setClosedAt(null);
        fonds.setClosureReason(null);
        ArchiveFonds updated = fondsRepository.update(fonds);
        insertEvent(
                updated,
                ArchiveFondsEventType.REOPENED,
                ArchiveFondsStatus.CLOSED.name(),
                ArchiveFondsStatus.ACTIVE.name(),
                reason,
                effectiveAt,
                userId);
        return toDto(updated);
    }

    public List<ArchiveFondsEventDto> listEvents(Long id) {
        ArchiveFonds fonds = loadFonds(id);
        return eventRepository.findByFondsCode(fonds.getFondsCode()).stream()
                .map(this::toEventDto)
                .toList();
    }

    private void insertEvent(
            ArchiveFonds fonds,
            ArchiveFondsEventType eventType,
            @Nullable String previousValue,
            @Nullable String currentValue,
            String reason,
            LocalDateTime effectiveAt,
            Long userId) {
        ArchiveFondsEvent event = new ArchiveFondsEvent();
        event.setFondsCode(fonds.getFondsCode());
        event.setEventType(eventType);
        event.setPreviousValue(previousValue);
        event.setCurrentValue(currentValue);
        event.setReason(reason);
        event.setEffectiveAt(effectiveAt);
        event.setOperatedBy(userId);
        eventRepository.insert(event);
    }

    private ArchiveFonds loadFonds(@Nullable Long id) {
        requireId(id);
        return fondsRepository.findById(id).orElseThrow(() -> notFound("全宗不存在"));
    }

    private ArchiveFondsDto toDto(ArchiveFonds fonds) {
        return new ArchiveFondsDto(
                fonds.getId(),
                fonds.getFondsCode(),
                fonds.getFondsNo(),
                fonds.getFondsName(),
                fonds.getStatus(),
                fonds.getNumberAssignedBy(),
                fonds.getNumberAssignedAt(),
                fonds.getStartDate(),
                fonds.getEndDate(),
                fonds.getHistoryNote(),
                fonds.getClosedAt(),
                fonds.getClosureReason(),
                fonds.getSortOrder(),
                fonds.getCreatedAt(),
                fonds.getUpdatedAt());
    }

    private ArchiveFondsEventDto toEventDto(ArchiveFondsEvent event) {
        return new ArchiveFondsEventDto(
                event.getId(),
                event.getFondsCode(),
                event.getEventType(),
                event.getPreviousValue(),
                event.getCurrentValue(),
                event.getReason(),
                event.getEffectiveAt(),
                event.getOperatedBy(),
                event.getCreatedAt());
    }

    private LocalDateTime resolveEffectiveAt(@Nullable LocalDateTime requested) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime effectiveAt = requested == null ? now : requested;
        if (effectiveAt.isAfter(now)) {
            throw new BadRequestException("生效时间不能晚于当前时间", "effectiveAt", "生效时间不能晚于当前时间");
        }
        return effectiveAt;
    }

    private void validateDates(@Nullable LocalDate startDate, @Nullable LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("终止日期不能早于起始日期", "endDate", "终止日期不能早于起始日期");
        }
    }

    private String requireReason(@Nullable String reason) {
        return requireText(reason, "原因不能为空", "reason", 500);
    }

    private String requireText(
            @Nullable String value, String message, String field, int maxLength) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message, field, message);
        }
        if (normalized.length() > maxLength) {
            throw new BadRequestException(
                    field + " 长度不能超过 " + maxLength, field, "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private @Nullable String optionalText(@Nullable String value, String field, int maxLength) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BadRequestException(
                    field + " 长度不能超过 " + maxLength, field, "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private void requireId(@Nullable Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("ID 不合法");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException conflict(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }
}
