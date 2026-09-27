package github.luckygc.am.infrastructure.runtime;

import java.util.List;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupService;

@Configuration(proxyBeanMethods = false)
public class ExpiredDataCleanupQuartzConfiguration {

    @Bean
    public ExpiredDataCleanupService expiredDataCleanupService(List<ExpiredDataCleaner> cleaners) {
        return new ExpiredDataCleanupService(cleaners);
    }

    @Bean
    public JobDetail expiredDataCleanupJobDetail() {
        return JobBuilder.newJob(ExpiredDataCleanupJob.class)
                .withIdentity("expired_data_cleanup_job", "system")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger expiredDataCleanupTrigger(JobDetail expiredDataCleanupJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(expiredDataCleanupJobDetail)
                .withIdentity("expired_data_cleanup_trigger", "system")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/10 * * * ?"))
                .build();
    }
}
