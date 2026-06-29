package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.ArchiveItemRoutingService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.ArchiveMetadataService.ArchiveFondsDto;

@Service
public class ArchiveVolumeService {

    private final ArchiveMapper archiveMapper;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveItemRoutingService archiveItemRoutingService;

    public ArchiveVolumeService(
            ArchiveMapper archiveMapper,
            ArchiveMetadataService archiveMetadataService,
            ArchiveItemRoutingService archiveItemRoutingService) {
        this.archiveMapper = archiveMapper;
        this.archiveMetadataService = archiveMetadataService;
        this.archiveItemRoutingService = archiveItemRoutingService;
    }

    public List<ArchiveVolumeDto> listVolumes(String fondsCode, String categoryCode) {
        return archiveMapper
                .listArchiveVolumes(
                        StringUtils.trimToNull(fondsCode), StringUtils.trimToNull(categoryCode))
                .stream()
                .map(this::toVolumeDto)
                .toList();
    }

    public ArchiveVolumeDto getVolume(Long id) {
        Map<String, Object> row = archiveMapper.getArchiveVolume(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "案卷不存在");
        }
        return toVolumeDto(row);
    }

    @Transactional
    public ArchiveVolumeDto createVolume(ArchiveVolumeRequest request, Long userId) {
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (request.categoryId() == null) {
            throw new BadRequestException("档案分类不能为空");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw new BadRequestException("全宗不能为空");
        }
        ArchiveCategoryDto category = archiveMetadataService.getCategory(request.categoryId());
        if (category.managementMode() != ArchiveManagementMode.VOLUME_ITEM) {
            throw new BadRequestException("该分类未启用案卷管理");
        }
        ArchiveFondsDto fonds = archiveMetadataService.getFondsByCode(request.fondsCode());
        int archiveYear =
                request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        Long id =
                archiveMapper.insertArchiveVolume(
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        category.categoryCode(),
                        category.categoryName(),
                        StringUtils.trimToNull(request.archiveNo()),
                        StringUtils.defaultIfBlank(request.electronicStatus(), "DRAFT"),
                        archiveYear,
                        userId);
        return getVolume(id);
    }

    @Transactional
    public void addItemToVolume(
            Long volumeId, Long archiveItemId, Integer displayOrder, Long userId) {
        ArchiveVolumeDto volume = getVolume(volumeId);
        archiveItemRoutingService.ensureItemEditable(archiveItemId);
        ArchiveItemDto item = archiveItemRoutingService.getItem(archiveItemId);
        if (!volume.fondsCode().equals(item.fondsCode())
                || !volume.categoryCode().equals(item.categoryCode())) {
            throw new BadRequestException("案卷和档案条目不属于同一全宗和分类");
        }
        int updated =
                archiveMapper.moveItemToVolume(
                        volumeId, archiveItemId, displayOrder == null ? 0 : displayOrder, userId);
        if (updated == 0) {
            throw new BadRequestException("档案条目已锁定或不存在，不能加入案卷");
        }
    }

    private ArchiveVolumeDto toVolumeDto(Map<String, Object> row) {
        return new ArchiveVolumeDto(
                number(row, "id").longValue(),
                string(row, "fondsCode"),
                string(row, "fondsName"),
                string(row, "categoryCode"),
                string(row, "categoryName"),
                string(row, "archiveNo"),
                string(row, "electronicStatus"),
                number(row, "archiveYear").intValue(),
                bool(row, "lockedFlag"),
                string(row, "lockReason"),
                longOrNull(row, "lockedBy"),
                dateTime(row, "lockedAt"));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return Boolean.TRUE.equals(value);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveVolumeRequest(
            Long categoryId,
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String electronicStatus) {}

    public record AddItemToVolumeRequest(Long itemId, Integer displayOrder) {}

    public record ArchiveVolumeDto(
            Long id,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            String archiveNo,
            String electronicStatus,
            int archiveYear,
            boolean lockedFlag,
            String lockReason,
            Long lockedBy,
            LocalDateTime lockedAt) {}
}
