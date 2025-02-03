package ch.admin.bit.jeap.errorhandling.domain.resend.scheduler;

import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ScheduledResend;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ScheduledResendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
class ResendScheduler {
    final ScheduledResendRepository scheduledResendRepository;
    final ResendSchedulerConfigProperties resendSchedulerConfigProperties;
    final ScheduledResendService scheduledResendService;
    final ErrorService errorService;
    final PlatformTransactionManager platformTransactionManager;

    @Scheduled(cron = "#{@resendSchedulerConfigProperties.cronExpression}")
    @SchedulerLock(name = "execute-pending-schedules", lockAtLeastFor = "#{@resendSchedulerConfigProperties.lockAtLeast.toString()}", lockAtMostFor = "#{@resendSchedulerConfigProperties.lockAtMost.toString()}")
    public void executePendingSchedules() {
        LockAssert.assertLocked();
        int processedChunks = 0;
        while (processedChunks < resendSchedulerConfigProperties.getMaxConsecutiveChunks()) {
            log.trace("Fetching at max {} unsent scheduled resend items ready to be sent now.", resendSchedulerConfigProperties.getMaxResendChunkSize());
            List<ScheduledResend> chunk = scheduledResendRepository.findNextScheduledResendsOldestFirst(ZonedDateTime.now(), resendSchedulerConfigProperties.getMaxResendChunkSize());
            if (chunk.isEmpty()) {
                log.trace("No unsent scheduled resend items found. Waiting for next execution...");
                return;
            }
            log.debug("Got {} unsent scheduled resend items in chunk #{}.", chunk.size(), processedChunks);
            chunk.forEach(errorService::scheduledResend);
            processedChunks++;
        }
        log.debug("Maximum number of consecutive chunks reached. Proceeding at next execution...");
    }

}
