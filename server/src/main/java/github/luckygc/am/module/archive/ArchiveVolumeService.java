package github.luckygc.am.module.archive;

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
        ArchiveRecordDto volume = archiveRecordRoutingService.getRecord(volumeId);
        archiveRecordRoutingService.ensureRecordEditable(volumeId);
        if (volume.archiveLevel() != ArchiveLevel.VOLUME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标记录不是案卷");
        }
        ArchiveRecordDto record = archiveRecordRoutingService.getRecord(archiveRecordId);
        if (record.archiveLevel() != ArchiveLevel.ITEM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能将卷内条目加入案卷");
        }
        if (!volume.fondsCode().equals(record.fondsCode())
                || !volume.categoryCode().equals(record.categoryCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "案卷和档案记录不属于同一全宗和分类");
        }
        int updated =
                archiveMapper.moveRecordToVolume(
                        volumeId, archiveRecordId, displayOrder == null ? 0 : displayOrder);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "卷内条目已锁定或不存在，不能加入案卷");
        }
    }
}
