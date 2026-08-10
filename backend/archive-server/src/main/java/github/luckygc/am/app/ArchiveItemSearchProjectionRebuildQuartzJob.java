package github.luckygc.am.app;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

import github.luckygc.am.module.archive.item.service.ArchiveItemSearchProjectionRebuildProcessor;

@DisallowConcurrentExecution
public class ArchiveItemSearchProjectionRebuildQuartzJob extends QuartzJobBean {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ArchiveItemSearchProjectionRebuildQuartzJob.class);

    private final ArchiveItemSearchProjectionRebuildProcessor processor;

    public ArchiveItemSearchProjectionRebuildQuartzJob(
            ArchiveItemSearchProjectionRebuildProcessor processor) {
        this.processor = processor;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        processor.processLegacyOutboxBatch();
        Long jobId = processor.nextJobId();
        if (jobId == null) {
            return;
        }
        try {
            processor.markRunning(jobId);
            processor.processNextBatch(jobId);
        } catch (RuntimeException exception) {
            LOGGER.warn("搜索投影重建任务执行失败：{}", jobId, exception);
            try {
                processor.markFailed(jobId, exception);
            } catch (RuntimeException markFailedException) {
                exception.addSuppressed(markFailedException);
                throw new JobExecutionException(exception);
            }
        }
    }
}
