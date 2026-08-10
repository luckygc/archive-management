package github.luckygc.am.app;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ArchiveItemSearchProjectionRebuildQuartzConfiguration {

    @Bean
    public JobDetail archiveItemSearchProjectionRebuildJobDetail() {
        return JobBuilder.newJob(ArchiveItemSearchProjectionRebuildQuartzJob.class)
                .withIdentity("archive_item_search_projection_rebuild_job", "archive")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger archiveItemSearchProjectionRebuildTrigger(
            JobDetail archiveItemSearchProjectionRebuildJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(archiveItemSearchProjectionRebuildJobDetail)
                .withIdentity("archive_item_search_projection_rebuild_trigger", "archive")
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInSeconds(5)
                                .repeatForever())
                .build();
    }
}
