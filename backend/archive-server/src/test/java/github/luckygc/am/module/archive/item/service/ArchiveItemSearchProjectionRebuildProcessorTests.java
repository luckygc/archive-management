package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import jakarta.data.Limit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;
import github.luckygc.am.module.archive.item.repository.ArchiveItemSearchProjectionRebuildJobDataRepository;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@DisplayName("搜索投影重建任务处理器")
class ArchiveItemSearchProjectionRebuildProcessorTests {

    private final ArchiveItemSearchProjectionRebuildJobDataRepository repository =
            mock(ArchiveItemSearchProjectionRebuildJobDataRepository.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemSearchProjectionSynchronizer synchronizer =
            mock(ArchiveItemSearchProjectionSynchronizer.class);
    private final ArchiveItemSearchProjectionRebuildProcessor processor =
            new ArchiveItemSearchProjectionRebuildProcessor(
                    repository, archiveMapper, synchronizer);

    @Test
    @DisplayName("超过一百条记录会跨批次全部完成")
    void processAllItemsAcrossBatches() {
        ArchiveItemSearchProjectionRebuildJob job = queuedJob(250L, 250);
        when(repository.findById(17L)).thenReturn(Optional.of(job));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(archiveMapper.listItemIdsForSearchRebuild(
                        eq("am_archive_item_contract"), anyLong(), eq(250L), anyInt()))
                .thenReturn(ids(1, 101), ids(101, 201), ids(201, 250));

        processor.markRunning(17L);
        processor.processNextBatch(17L);

        assertThat(job.getStatus()).isEqualTo(ArchiveItemSearchProjectionRebuildJobStatus.QUEUED);
        assertThat(job.getProcessedCount()).isEqualTo(100);

        processor.markRunning(17L);
        processor.processNextBatch(17L);
        processor.markRunning(17L);
        processor.processNextBatch(17L);

        assertThat(job.getStatus())
                .isEqualTo(ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED);
        assertThat(job.getProcessedCount()).isEqualTo(250);
        assertThat(job.getLastProcessedItemId()).isEqualTo(250L);
        verify(synchronizer, times(250)).synchronize(anyLong());
    }

    @Test
    @DisplayName("处理失败后任务进入可观察的失败状态")
    void markFailedStoresStableError() {
        ArchiveItemSearchProjectionRebuildJob job = queuedJob(10L, 10);
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.RUNNING);
        when(repository.findById(17L)).thenReturn(Optional.of(job));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.markFailed(17L, new IllegalStateException("字段定义失效"));

        assertThat(job.getStatus()).isEqualTo(ArchiveItemSearchProjectionRebuildJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("SEARCH_PROJECTION_REBUILD_FAILED");
        assertThat(job.getErrorMessage()).contains("字段定义失效");
    }

    @Test
    @DisplayName("处理器优先恢复中断的运行中任务")
    void nextJobPrefersInterruptedRunningJob() {
        ArchiveItemSearchProjectionRebuildJob running = queuedJob(10L, 10);
        running.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.RUNNING);
        when(repository.findByStatus(
                        ArchiveItemSearchProjectionRebuildJobStatus.RUNNING, Limit.of(1)))
                .thenReturn(List.of(running));

        assertThat(processor.nextJobId()).isEqualTo(17L);
    }

    private static ArchiveItemSearchProjectionRebuildJob queuedJob(Long maxItemId, int total) {
        ArchiveItemSearchProjectionRebuildJob job = new ArchiveItemSearchProjectionRebuildJob();
        job.setId(17L);
        job.setCategoryId(3L);
        job.setTableName("am_archive_item_contract");
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.QUEUED);
        job.setTotalCount(total);
        job.setMaxItemId(maxItemId);
        job.setCreatedAt(LocalDateTime.of(2026, 8, 9, 12, 0));
        job.setUpdatedAt(job.getCreatedAt());
        return job;
    }

    private static List<Long> ids(long firstInclusive, long lastInclusive) {
        return LongStream.rangeClosed(firstInclusive, lastInclusive).boxed().toList();
    }
}
