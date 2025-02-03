package ch.admin.bit.jeap.errorhandling.domain.resend.strategy;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMessage;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultResendingStrategy implements ResendingStrategy {
    private final DefaultResendingStrategyConfigProperties resendSchedulerConfigProperties;

    @Override
    public Optional<ZonedDateTime> determineResend(int errorCountForEvent, EventMetadata eventMetadata, EventMetadata errorEventMetadata, ErrorEventData errorEventData, EventMessage message) {
        if (errorCountForEvent < resendSchedulerConfigProperties.getMaxRetries()) {
            ZonedDateTime resendAt = ZonedDateTime.now().plus(computeDelay(errorCountForEvent));
            log.debug("Current error count for event {} is {} therefore resend it at {}", eventMetadata.getId(), errorCountForEvent, resendAt);
            return Optional.of(resendAt);
        } else {
            log.debug("Current error count for event {} is {}, do not resend it no more", eventMetadata.getId(), errorCountForEvent);
            return Optional.empty();
        }
    }

    Duration computeDelay(int currentRetryCount) {
        Duration delay = resendSchedulerConfigProperties.getDelay();

        if (resendSchedulerConfigProperties.isExponentialBackoffEnabled()) {
            // Computing the currentRetryCount power of the exponentialBackoffFactor using Duration
            // (taking advantage of Duration calculating in BigDecimals)
            for (int i = 0; i < currentRetryCount; i++) {
                delay = delay.multipliedBy(resendSchedulerConfigProperties.getExponentialBackoffFactor());
                if (delay.compareTo(resendSchedulerConfigProperties.getExponentialBackoffMaxDelay()) >= 0) {
                    delay = resendSchedulerConfigProperties.getExponentialBackoffMaxDelay();
                    break;
                }
            }
        }
        return delay;
    }
}
