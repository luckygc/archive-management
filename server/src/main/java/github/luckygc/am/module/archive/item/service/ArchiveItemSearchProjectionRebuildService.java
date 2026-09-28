package github.luckygc.am.module.archive.item.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;
import github.luckygc.am.module.archive.item.repository.ArchiveItemSearchProjectionRebuildJobDataRepository;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;

@Service
public class ArchiveItemSearchProjectionRebuildService {

    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveItemSearchProjectionRebuildJobDataRepository repository;

    public ArchiveItemSearchProjectionRebuildService(
            ArchiveCategoryService archiveCategoryService,
            ArchiveMapper archiveMapper,
            ArchiveItemSearchProjectionRebuildJobDataRepository repository) {
        this.archiveCategoryService = archiveCategoryService;
        this.archiveMapper = archiveMapper;
        this.repository = repository;
    }

    @Transactional
    public Long start(Long categoryId, Long requestedBy) {
        ArchiveCategoryDto category = archiveCategoryService.getCategory(categoryId);
        String tableName = ArchiveDynamicTableNames.tableName(category, ArchiveLevel.ITEM);
        if (archiveMapper.tableExists(tableName) == 0) {
            throw new BadRequestException("档案分类尚未建表");
        }
        @Nullable Long maxItemId = archiveMapper.getMaxItemIdForSearchRebuild(tableName);
        int totalCount =
                maxItemId == null
                        ? 0
                        : archiveMapper.countItemsForSearchRebuild(tableName, maxItemId);
        ArchiveItemSearchProjectionRebuildJob job = new ArchiveItemSearchProjectionRebuildJob();
        job.setCategoryId(categoryId);
        job.setTableName(tableName);
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.QUEUED);
        job.setTotalCount(totalCount);
        job.setMaxItemId(maxItemId);
        job.setRequestedBy(requestedBy);
        return repository.insert(job).getId();
    }
}
