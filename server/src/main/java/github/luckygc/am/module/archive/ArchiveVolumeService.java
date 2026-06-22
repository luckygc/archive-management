package github.luckygc.am.module.archive;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveRecordRoutingService.ArchiveRecordDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@Service
public class ArchiveVolumeService {

    private final ArchiveMapper archiveMapper;
    private final ArchiveRecordRoutingService archiveRecordRoutingService;

    public ArchiveVolumeService(
            ArchiveMapper archiveMapper, ArchiveRecordRoutingService archiveRecordRoutingService) {
        this.archiveMapper = archiveMapper;
        this.archiveRecordRoutingService = archiveRecordRoutingService;
    }

    @Transactional
    public void addRecordToVolume(Long volumeId, Long archiveRecordId, Integer displayOrder) {
        archiveRecordRoutingService.ensureRecordEditable(archiveRecordId);
        VolumeScope volume = findVolume(volumeId);
        ArchiveRecordDto record = archiveRecordRoutingService.getRecord(archiveRecordId);
        if (!volume.fondsCode().equals(record.fondsCode())
                || !volume.categoryCode().equals(record.categoryCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "案卷和档案记录不属于同一全宗和分类");
        }
        archiveMapper.insertVolumeItem(
                volumeId,
                archiveRecordId,
                record.fondsCode(),
                record.categoryCode(),
                displayOrder == null ? 0 : displayOrder);
    }

    private VolumeScope findVolume(Long volumeId) {
        Map<String, Object> row = archiveMapper.findVolumeScope(volumeId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "案卷不存在");
        }
        return new VolumeScope(row.get("fondsCode").toString(), row.get("categoryCode").toString());
    }

    private record VolumeScope(String fondsCode, String categoryCode) {}
}
