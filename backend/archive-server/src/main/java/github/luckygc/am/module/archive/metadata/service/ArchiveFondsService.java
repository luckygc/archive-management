package github.luckygc.am.module.archive.metadata.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsRequest;

@Service
public class ArchiveFondsService {

    private final ArchiveFondsDataRepository fondsRepository;

    public ArchiveFondsService(ArchiveFondsDataRepository fondsRepository) {
        this.fondsRepository = fondsRepository;
    }

    public List<ArchiveFondsDto> listFonds(@Nullable Boolean enabled) {
        List<ArchiveFonds> fonds =
                enabled == null ? fondsRepository.list() : fondsRepository.list(enabled);
        return fonds.stream().map(this::toDto).toList();
    }

    @Transactional
    public ArchiveFondsDto createFonds(ArchiveFondsRequest request, Long userId) {
        validateRequired(request.fondsCode(), "全宗编码不能为空");
        validateRequired(request.fondsName(), "全宗名称不能为空");
        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setFondsCode(request.fondsCode().trim());
        fonds.setFondsName(request.fondsName().trim());
        fonds.setEnabled(request.enabled() == null || request.enabled());
        fonds.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return toDto(fondsRepository.insert(fonds));
    }

    @Transactional
    public ArchiveFondsDto updateFonds(Long id, ArchiveFondsRequest request, Long userId) {
        requireId(id);
        validateRequired(request.fondsCode(), "全宗编码不能为空");
        validateRequired(request.fondsName(), "全宗名称不能为空");
        ArchiveFonds fonds = fondsRepository.findById(id).orElseThrow(() -> notFound("全宗不存在"));
        fonds.setFondsCode(request.fondsCode().trim());
        fonds.setFondsName(request.fondsName().trim());
        fonds.setEnabled(request.enabled() == null || request.enabled());
        fonds.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return toDto(fondsRepository.update(fonds));
    }

    @Transactional
    public void deleteFonds(Long id, Long userId) {
        requireId(id);
        ArchiveFonds fonds = fondsRepository.findById(id).orElseThrow(() -> notFound("全宗不存在"));
        fondsRepository.update(fonds);
        fondsRepository.delete(fonds);
    }

    private ArchiveFondsDto toDto(ArchiveFonds fonds) {
        return new ArchiveFondsDto(
                fonds.getId(),
                fonds.getFondsCode(),
                fonds.getFondsName(),
                fonds.isEnabled(),
                fonds.getSortOrder(),
                fonds.getCreatedAt(),
                fonds.getUpdatedAt());
    }

    private void requireId(@Nullable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID 不合法");
        }
    }

    private void validateRequired(@Nullable String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
