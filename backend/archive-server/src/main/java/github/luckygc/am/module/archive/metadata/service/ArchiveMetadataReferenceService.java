package github.luckygc.am.module.archive.metadata.service;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveClassificationScheme;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveRetentionPeriod;
import github.luckygc.am.module.archive.metadata.ArchiveSecurityLevel;
import github.luckygc.am.module.archive.metadata.repository.ArchiveClassificationSchemeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveRetentionPeriodDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveSecurityLevelDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveClassificationSchemeDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveClassificationSchemeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveRetentionPeriodDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveSecurityLevelDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveRetentionPeriodRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveSecurityLevelRequest;

@Service
public class ArchiveMetadataReferenceService {

    private static final Pattern SCHEME_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    private final ArchiveFondsDataRepository fondsRepository;
    private final ArchiveClassificationSchemeDataRepository classificationSchemeRepository;
    private final ArchiveSecurityLevelDataRepository securityLevelRepository;
    private final ArchiveRetentionPeriodDataRepository retentionPeriodRepository;

    public ArchiveMetadataReferenceService(
            ArchiveFondsDataRepository fondsRepository,
            ArchiveClassificationSchemeDataRepository classificationSchemeRepository,
            ArchiveSecurityLevelDataRepository securityLevelRepository,
            ArchiveRetentionPeriodDataRepository retentionPeriodRepository) {
        this.fondsRepository = fondsRepository;
        this.classificationSchemeRepository = classificationSchemeRepository;
        this.securityLevelRepository = securityLevelRepository;
        this.retentionPeriodRepository = retentionPeriodRepository;
    }

    public ArchiveFondsDto getFondsByCode(String fondsCode) {
        return mapFonds(loadFondsByCode(fondsCode));
    }

    public ArchiveFondsDto getFonds(Long id) {
        requireId(id);
        return fondsRepository
                .findById(id)
                .map(this::mapFonds)
                .orElseThrow(() -> notFound("全宗不存在"));
    }

    public ArchiveFondsDto getEnabledFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) throw new BadRequestException("全宗不可用");
        return fondsRepository
                .find(normalizedCode)
                .filter(ArchiveFonds::isEnabled)
                .map(this::mapFonds)
                .orElseThrow(() -> new BadRequestException("全宗不可用"));
    }

    public List<ArchiveClassificationSchemeDto> listClassificationSchemes(
            @Nullable Boolean enabled) {
        List<ArchiveClassificationScheme> schemes =
                enabled == null
                        ? classificationSchemeRepository.list()
                        : classificationSchemeRepository.list(enabled);
        return schemes.stream().map(this::mapClassificationScheme).toList();
    }

    @Transactional
    public ArchiveClassificationSchemeDto createClassificationScheme(
            ArchiveClassificationSchemeRequest request, Long userId) {
        ClassificationSchemeValues values = validateClassificationSchemeRequest(request);
        ensureSchemeCodeAvailable(values.schemeCode(), null);
        ArchiveClassificationScheme scheme = new ArchiveClassificationScheme();
        applyClassificationSchemeValues(scheme, values);
        return mapClassificationScheme(classificationSchemeRepository.insert(scheme));
    }

    @Transactional
    public ArchiveClassificationSchemeDto updateClassificationScheme(
            Long id, ArchiveClassificationSchemeRequest request, Long userId) {
        requireId(id);
        ClassificationSchemeValues values = validateClassificationSchemeRequest(request);
        ensureSchemeCodeAvailable(values.schemeCode(), id);
        ArchiveClassificationScheme scheme =
                classificationSchemeRepository.findById(id).orElseThrow(() -> notFound("分类方案不存在"));
        applyClassificationSchemeValues(scheme, values);
        return mapClassificationScheme(classificationSchemeRepository.update(scheme));
    }

    public List<ArchiveSecurityLevelDto> listSecurityLevels(@Nullable Boolean enabled) {
        List<ArchiveSecurityLevel> levels =
                enabled == null
                        ? securityLevelRepository.list()
                        : securityLevelRepository.list(enabled);
        return levels.stream().map(this::mapSecurityLevel).toList();
    }

    @Transactional
    public ArchiveSecurityLevelDto updateSecurityLevel(
            Long id, UpdateArchiveSecurityLevelRequest request) {
        requireId(id);
        String name = StringUtils.trimToNull(request.levelName());
        if (name == null) throw new BadRequestException("密级名称不能为空", "levelName", "密级名称不能为空");
        ArchiveSecurityLevel level =
                securityLevelRepository.findById(id).orElseThrow(() -> notFound("密级不存在"));
        level.setLevelName(name);
        return mapSecurityLevel(securityLevelRepository.update(level));
    }

    public List<ArchiveRetentionPeriodDto> listRetentionPeriods(@Nullable Boolean enabled) {
        List<ArchiveRetentionPeriod> periods =
                enabled == null
                        ? retentionPeriodRepository.list()
                        : retentionPeriodRepository.list(enabled);
        return periods.stream().map(this::mapRetentionPeriod).toList();
    }

    @Transactional
    public ArchiveRetentionPeriodDto updateRetentionPeriod(
            Long id, UpdateArchiveRetentionPeriodRequest request) {
        requireId(id);
        String name = StringUtils.trimToNull(request.periodName());
        if (name == null) throw new BadRequestException("保管期限名称不能为空", "periodName", "保管期限名称不能为空");
        ArchiveRetentionPeriod period =
                retentionPeriodRepository.findById(id).orElseThrow(() -> notFound("保管期限不存在"));
        period.setPeriodName(name);
        return mapRetentionPeriod(retentionPeriodRepository.update(period));
    }

    private ClassificationSchemeValues validateClassificationSchemeRequest(
            ArchiveClassificationSchemeRequest request) {
        String schemeCode = StringUtils.trimToNull(request.schemeCode());
        String schemeName = StringUtils.trimToNull(request.schemeName());
        validateRequired(schemeCode, "分类方案编码不能为空");
        validateRequired(schemeName, "分类方案名称不能为空");
        if (!SCHEME_CODE_PATTERN.matcher(schemeCode).matches())
            throw new BadRequestException("分类方案编码只允许小写字母、数字和下划线，并且必须以小写字母开头");
        return new ClassificationSchemeValues(
                schemeCode,
                schemeName,
                StringUtils.trimToNull(request.description()),
                request.enabled() == null || request.enabled(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void applyClassificationSchemeValues(
            ArchiveClassificationScheme scheme, ClassificationSchemeValues values) {
        scheme.setSchemeCode(values.schemeCode());
        scheme.setSchemeName(values.schemeName());
        scheme.setDescription(values.description());
        scheme.setEnabled(values.enabled());
        scheme.setSortOrder(values.sortOrder());
    }

    private void ensureSchemeCodeAvailable(String schemeCode, @Nullable Long currentId) {
        ArchiveClassificationScheme existing =
                classificationSchemeRepository.findBySchemeCode(schemeCode);
        if (existing != null && !existing.getId().equals(currentId))
            throw new BadRequestException("分类方案编码已存在");
    }

    private ArchiveFonds loadFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) throw notFound("全宗不存在");
        return fondsRepository.find(normalizedCode).orElseThrow(() -> notFound("全宗不存在"));
    }

    private void validateRequired(@Nullable String value, String message) {
        if (value == null) throw new BadRequestException(message);
    }

    private void requireId(@Nullable Long id) {
        if (id == null) throw new BadRequestException("ID 不能为空");
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ArchiveFondsDto mapFonds(ArchiveFonds fonds) {
        return new ArchiveFondsDto(
                fonds.getId(),
                fonds.getFondsCode(),
                fonds.getFondsName(),
                fonds.isEnabled(),
                fonds.getSortOrder(),
                fonds.getCreatedAt(),
                fonds.getUpdatedAt());
    }

    private ArchiveClassificationSchemeDto mapClassificationScheme(
            ArchiveClassificationScheme scheme) {
        return new ArchiveClassificationSchemeDto(
                scheme.getId(),
                scheme.getSchemeCode(),
                scheme.getSchemeName(),
                scheme.getDescription(),
                scheme.isDefaultFlag(),
                scheme.isEnabled(),
                scheme.getSortOrder(),
                scheme.getCreatedAt(),
                scheme.getUpdatedAt());
    }

    private ArchiveSecurityLevelDto mapSecurityLevel(ArchiveSecurityLevel level) {
        return new ArchiveSecurityLevelDto(
                level.getId(),
                level.getLevelName(),
                level.isEnabled(),
                level.getSortOrder(),
                level.getCreatedAt(),
                level.getUpdatedAt());
    }

    private ArchiveRetentionPeriodDto mapRetentionPeriod(ArchiveRetentionPeriod period) {
        return new ArchiveRetentionPeriodDto(
                period.getId(),
                period.getPeriodName(),
                period.isEnabled(),
                period.getSortOrder(),
                period.getCreatedAt(),
                period.getUpdatedAt());
    }

    private record ClassificationSchemeValues(
            String schemeCode,
            String schemeName,
            @Nullable String description,
            boolean enabled,
            int sortOrder) {}
}
