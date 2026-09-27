package github.luckygc.am.module.archive.item.service;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.JobAcceptedResponse;
import github.luckygc.am.common.api.JobStatusResponse;
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

    private static final String JOB_RESOURCE_PATH =
            "/api/v1/archive-search-projection-rebuild-jobs/";

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
    public JobAcceptedResponse start(Long categoryId, Long requestedBy) {
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
        ArchiveItemSearchProjectionRebuildJob created = repository.insert(job);
        String operationLocation = JOB_RESOURCE_PATH + created.getId();
        return new JobAcceptedResponse(
                created.getId(), created.getStatus().apiValue(), operationLocation);
    }

    @Transactional(readOnly = true)
    public JobStatusResponse get(Long jobId) {
        ArchiveItemSearchProjectionRebuildJob job =
                repository
                        .findById(jobId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "搜索投影重建任务不存在"));
        return new JobStatusResponse(
                job.getId(),
                job.getStatus().apiValue(),
                progress(job),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                result(job),
                job.getErrorCode(),
                job.getErrorMessage());
    }

    private int progress(ArchiveItemSearchProjectionRebuildJob job) {
        if (job.getStatus() == ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED) {
            return 100;
        }
        if (job.getTotalCount() == 0) {
            return 0;
        }
        return (int) Math.min(99L, (long) job.getProcessedCount() * 100L / job.getTotalCount());
    }

    private @Nullable Map<String, Object> result(ArchiveItemSearchProjectionRebuildJob job) {
        if (job.getStatus() != ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED) {
            return null;
        }
        return Map.of("categoryId", job.getCategoryId(), "rebuiltCount", job.getProcessedCount());
    }
}
