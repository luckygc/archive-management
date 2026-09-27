package github.luckygc.am.module.archive.item.service;

import java.util.List;

import jakarta.data.Limit;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;
import github.luckygc.am.module.archive.item.repository.ArchiveItemSearchProjectionRebuildJobDataRepository;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@Service
public class ArchiveItemSearchProjectionRebuildProcessor {

    static final int BATCH_SIZE = 100;
    private static final int ERROR_MESSAGE_LIMIT = 1000;
    private static final String ERROR_CODE = "SEARCH_PROJECTION_REBUILD_FAILED";

    private final ArchiveItemSearchProjectionRebuildJobDataRepository repository;
    private final ArchiveMapper archiveMapper;
    private final ArchiveItemSearchProjectionSynchronizer synchronizer;

    public ArchiveItemSearchProjectionRebuildProcessor(
            ArchiveItemSearchProjectionRebuildJobDataRepository repository,
            ArchiveMapper archiveMapper,
            ArchiveItemSearchProjectionSynchronizer synchronizer) {
        this.repository = repository;
        this.archiveMapper = archiveMapper;
        this.synchronizer = synchronizer;
    }

    @Transactional(readOnly = true)
    public @Nullable Long nextJobId() {
        List<ArchiveItemSearchProjectionRebuildJob> interrupted =
                repository.findByStatus(
                        ArchiveItemSearchProjectionRebuildJobStatus.RUNNING, Limit.of(1));
        if (!interrupted.isEmpty()) {
            return interrupted.getFirst().getId();
        }
        List<ArchiveItemSearchProjectionRebuildJob> queued =
                repository.findByStatus(
                        ArchiveItemSearchProjectionRebuildJobStatus.QUEUED, Limit.of(1));
        return queued.isEmpty() ? null : queued.getFirst().getId();
    }

    @Transactional
    public void markRunning(Long jobId) {
        ArchiveItemSearchProjectionRebuildJob job = load(jobId);
        if (job.getStatus() != ArchiveItemSearchProjectionRebuildJobStatus.QUEUED
                && job.getStatus() != ArchiveItemSearchProjectionRebuildJobStatus.RUNNING) {
            return;
        }
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.RUNNING);
        repository.update(job);
    }

    @Transactional
    public void processNextBatch(Long jobId) {
        ArchiveItemSearchProjectionRebuildJob job = load(jobId);
        if (job.getStatus() != ArchiveItemSearchProjectionRebuildJobStatus.RUNNING) {
            return;
        }
        if (job.getMaxItemId() == null) {
            succeed(job);
            return;
        }
        long afterId = job.getLastProcessedItemId() == null ? 0L : job.getLastProcessedItemId();
        List<Long> candidates =
                archiveMapper.listItemIdsForSearchRebuild(
                        job.getTableName(), afterId, job.getMaxItemId(), BATCH_SIZE + 1);
        boolean hasMore = candidates.size() > BATCH_SIZE;
        List<Long> batch = hasMore ? candidates.subList(0, BATCH_SIZE) : candidates;
        for (Long itemId : batch) {
            synchronizer.synchronize(itemId);
        }
        if (!batch.isEmpty()) {
            job.setLastProcessedItemId(batch.getLast());
            job.setProcessedCount(job.getProcessedCount() + batch.size());
        }
        if (hasMore) {
            job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.QUEUED);
            repository.update(job);
            return;
        }
        succeed(job);
    }

    @Transactional
    public void markFailed(Long jobId, RuntimeException exception) {
        ArchiveItemSearchProjectionRebuildJob job = load(jobId);
        if (job.getStatus() == ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED
                || job.getStatus() == ArchiveItemSearchProjectionRebuildJobStatus.CANCELLED) {
            return;
        }
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.FAILED);
        job.setErrorCode(ERROR_CODE);
        String message =
                StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getName());
        job.setErrorMessage(StringUtils.truncate(message, ERROR_MESSAGE_LIMIT));
        repository.update(job);
    }

    public void processLegacyOutboxBatch() {
        synchronizer.drainOutbox();
    }

    private void succeed(ArchiveItemSearchProjectionRebuildJob job) {
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED);
        repository.update(job);
    }

    private ArchiveItemSearchProjectionRebuildJob load(Long jobId) {
        return repository
                .findById(jobId)
                .orElseThrow(() -> new IllegalStateException("搜索投影重建任务不存在"));
    }
}
