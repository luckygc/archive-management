package github.luckygc.am.infrastructure.runtime;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class ExpiredDataCleanupJob extends QuartzJobBean {

    private final ExpiredDataCleanupService cleanupService;

    public ExpiredDataCleanupJob(ExpiredDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        cleanupService.cleanupExpiredData();
    }
}
