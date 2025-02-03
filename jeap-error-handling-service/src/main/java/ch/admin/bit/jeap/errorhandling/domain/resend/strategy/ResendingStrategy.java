package ch.admin.bit.jeap.errorhandling.domain.resend.strategy;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMessage;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMetadata;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Strategy interface for implementing a custom resend strategy used by the ErrorHandlingService.
 * Default implementation is {@link DefaultResendingStrategy} but you can provide your own
 */
public interface ResendingStrategy {
    /**
     * Check if an event shall be resend
     *
     * @param errorCountForEvent The number of times an error has occurred for this event (i.e. retry count)
     * @param eventMetadata      The metadata of the event in error
     * @param errorEventMetadata The metadata of the error event that reported the event in error
     * @param errorEventData     The error reported for the event in error
     * @param message            The message used to transport the event in error
     * @return Time to resend or {@link Optional#empty()} if the message shall not be resent
     */
    Optional<ZonedDateTime> determineResend(int errorCountForEvent, EventMetadata eventMetadata,
                                            @SuppressWarnings("unused") EventMetadata errorEventMetadata,
                                            @SuppressWarnings("unused") ErrorEventData errorEventData,
                                            @SuppressWarnings("unused") EventMessage message);
}
