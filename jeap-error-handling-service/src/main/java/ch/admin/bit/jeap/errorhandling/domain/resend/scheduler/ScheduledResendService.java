package ch.admin.bit.jeap.errorhandling.domain.resend.scheduler;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ScheduledResend;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ScheduledResendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ScheduledResendService {
    final ScheduledResendRepository scheduledResendRepository;

    /**
     * Schedule the resending of the event that caused the error identified by the given id at the time given .
     */
    public void scheduleResend(UUID errorId, ZonedDateTime resendAt) {
        ScheduledResend scheduledResend = new ScheduledResend(errorId, resendAt);
        log.debug("Create scheduled resend {}.", scheduledResend);
        scheduledResendRepository.save(scheduledResend);
    }

    @Transactional(readOnly = true)
    public ZonedDateTime getNextResendTimestamp(UUID errrorId) {
        Optional<ScheduledResend> nextResend = scheduledResendRepository.findNextScheduledResend(errrorId);
        return nextResend.map(ScheduledResend::getResendAt).orElse(null);
    }

    public void cancelScheduledResends(Error error) {
        if (error.getState() == Error.ErrorState.TEMPORARY_RETRY_PENDING) {
            List<ScheduledResend> scheduledResends = scheduledResendRepository.findByErrorId(error.getId());
            log.info("Cancelling {} scheduled resends for error {}", scheduledResends.size(), error.getId());
            scheduledResends.forEach(ScheduledResend::cancel);
        }
    }

    public void setResent(ScheduledResend scheduledResend) {
        log.debug("Setting resentAt for {} to now.", scheduledResend.getId());
        scheduledResend.setResentAt(ZonedDateTime.now());
        //We have to manually save the schedules resend service as its not originating from this transaction
        scheduledResendRepository.save(scheduledResend);
    }
}
