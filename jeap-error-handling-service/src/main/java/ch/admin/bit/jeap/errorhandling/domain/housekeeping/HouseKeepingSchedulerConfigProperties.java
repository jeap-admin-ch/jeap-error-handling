package ch.admin.bit.jeap.errorhandling.domain.housekeeping;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Configuration for the automatic housekeeping scheduler (Db clean up)
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.housekeeping.scheduler")
@Data
public class HouseKeepingSchedulerConfigProperties {

    /**
     * How often to run the scheduler? Must be a cron expression. Default: Once a Day at 12:40
     * see {@link org.springframework.scheduling.support.CronExpression}
     */
    private String cronExpression = "0 40 00 * * *"; // every day at 00:40

    /**
     * Minimal time to keep a lock at this job,
     * see {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock}
     */
    private Duration lockAtLeast = Duration.of(5, ChronoUnit.SECONDS);
    /**
     * Maximal time to keep a lock at this job,
     * see {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock}
     */
    private Duration lockAtMost = Duration.of(30, ChronoUnit.MINUTES);

}
