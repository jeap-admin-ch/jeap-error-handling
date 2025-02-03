package ch.admin.bit.jeap.errorhandling.domain.resend.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Configuration for the automatic resend scheduler
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.resend.scheduler")
@Data
public class ResendSchedulerConfigProperties {
    /**
     * Maximal number of chunks to resend in one run
     */
    private int maxConsecutiveChunks = 50;
    /**
     * Maximal of events to send in one chunk
     */
    private int maxResendChunkSize = 20;
    /**
     * How often to run the scheduler? Must be a cron expression,
     * see {@link org.springframework.scheduling.support.CronExpression}
     */
    private String cronExpression = "*/10 * * * * *";
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
