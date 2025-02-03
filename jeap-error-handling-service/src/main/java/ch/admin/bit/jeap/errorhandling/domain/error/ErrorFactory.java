package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.UUID;

@Component
public class ErrorFactory {

    Error newTemporaryErrorFromTemplate(Error originalError) {
        return originalError.toBuilder()
                .id(randomErrorId())
                .state(Error.ErrorState.TEMPORARY_RETRY_PENDING)
                .created(ZonedDateTime.now())
                .originalTraceContext(originalError.getOriginalTraceContext())
                .modified(null)
                .build();
    }

    UUID randomErrorId() {
        return UUID.randomUUID();
    }
}
