package github.luckygc.am.app;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionRebuildProcessor;

@DisplayName("搜索投影重建 Quartz 任务")
class ArchiveItemSearchProjectionRebuildJobTests {

    private final ArchiveItemSearchProjectionRebuildProcessor processor =
            mock(ArchiveItemSearchProjectionRebuildProcessor.class);
    private final ArchiveItemSearchProjectionRebuildQuartzJob job =
            new ArchiveItemSearchProjectionRebuildQuartzJob(processor);

    @Test
    @DisplayName("每次调度消费遗留事件并处理一个有界批次")
    void executionProcessesOneBatch() throws Exception {
        when(processor.nextJobId()).thenReturn(17L);

        job.executeInternal(mock(JobExecutionContext.class));

        verify(processor).processLegacyOutboxBatch();
        verify(processor).markRunning(17L);
        verify(processor).processNextBatch(17L);
    }

    @Test
    @DisplayName("批次失败后通过独立事务记录失败状态")
    void executionMarksFailedJob() throws Exception {
        IllegalStateException failure = new IllegalStateException("字段定义失效");
        when(processor.nextJobId()).thenReturn(17L);
        doThrow(failure).when(processor).processNextBatch(17L);

        job.executeInternal(mock(JobExecutionContext.class));

        verify(processor).markFailed(17L, failure);
    }
}
