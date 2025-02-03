package ch.admin.bit.jeap.errorhandling.domain.manualtask.resend;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Configuration properties for {@link TasksSynchronize}
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.task-management.synchronize")
@Data
class TasksSynchronizeProperties {
    /**
     * Maximal number of chunks to resend in one run
     */
    private int maxConsecutiveChunks = 10;
    /**
     * Maximal of errors to send in one chunk
     */
    private int maxResendChunkSize = 100;
    /**
     * How often to run the synchronize? Must be a cron expression,
     * see {@link org.springframework.scheduling.support.CronSequenceGenerator}
     */
    private String cronExpression = "0 */5 * * * *";
    /**
     * Minimal time to keep a lock at this job,
     * see {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock}
     */
    private Duration lockAtLeast = Duration.of(5, ChronoUnit.SECONDS);
    /**
     * Maximal time to keep a lock at this job,
     * see {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock
     */
    private Duration lockAtMost = Duration.of(30, ChronoUnit.MINUTES);
}
