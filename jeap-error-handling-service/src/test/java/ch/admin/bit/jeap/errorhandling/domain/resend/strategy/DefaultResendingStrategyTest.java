package ch.admin.bit.jeap.errorhandling.domain.resend.strategy;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventPublisher;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultResendingStrategyTest {
    private static final int MAX_DELAY = 100;
    private static final int MAX_RETRIES = 5;

    @Test
    void computeDelay_withExponentialBackoff() {
        DefaultResendingStrategy strategy = createStrategyWithExponentialBackoff();

        assertEquals(5, strategy.computeDelay(0).getSeconds());
        assertEquals(15, strategy.computeDelay(1).getSeconds());
        assertEquals(45, strategy.computeDelay(2).getSeconds());
        assertEquals(MAX_DELAY, strategy.computeDelay(3).getSeconds());
    }

    @Test
    void computeDelay_withoutExponentialBackoff() {
        DefaultResendingStrategy strategy = createStrategyWithoutExponentialBackoff();

        assertEquals(5, strategy.computeDelay(0).getSeconds());
        assertEquals(5, strategy.computeDelay(2).getSeconds());
    }

    @Test
    void determineResend() {
        DefaultResendingStrategy strategy = createStrategyWithExponentialBackoff();
        EventMetadata eventMetadata = EventMetadata.builder()
                .id("event-id")
                .idempotenceId("idempotence-id")
                .created(ZonedDateTime.now())
                .type(EventType.builder().name("name").version("1").build())
                .publisher(EventPublisher.builder().system("system").service("service").build())
                .build();

        Optional<ZonedDateTime> resendRequest = strategy.determineResend(0, eventMetadata, null, null, null);
        assertTrue(resendRequest.isPresent());
        assertNotNull(resendRequest.get());

        int errorCount = MAX_RETRIES + 1;
        Optional<ZonedDateTime> doNotResendRequest = strategy.determineResend(errorCount, eventMetadata, null, null, null);
        assertFalse(doNotResendRequest.isPresent());
    }

    DefaultResendingStrategy createStrategyWithExponentialBackoff() {
        DefaultResendingStrategyConfigProperties props = new DefaultResendingStrategyConfigProperties();
        props.setMaxRetries(MAX_RETRIES);
        props.setDelay(Duration.ofSeconds(5));
        props.setExponentialBackoffEnabled(true);
        props.setExponentialBackoffFactor(3);
        props.setExponentialBackoffMaxDelay(Duration.ofSeconds(MAX_DELAY));
        return new DefaultResendingStrategy(props);
    }

    DefaultResendingStrategy createStrategyWithoutExponentialBackoff() {
        DefaultResendingStrategyConfigProperties props = new DefaultResendingStrategyConfigProperties();
        props.setMaxRetries(MAX_RETRIES);
        props.setDelay(Duration.ofSeconds(5));
        props.setExponentialBackoffEnabled(false);
        return new DefaultResendingStrategy(props);
    }
}
