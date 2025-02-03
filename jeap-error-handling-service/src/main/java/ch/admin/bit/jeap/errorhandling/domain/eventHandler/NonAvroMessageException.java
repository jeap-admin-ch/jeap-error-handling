package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;

class NonAvroMessageException extends RuntimeException {
    private NonAvroMessageException(String message, Exception cause) {
        super(message, cause);
    }

    static NonAvroMessageException of(MessageProcessingFailedEvent messageProcessingFailedEvent, Exception cause) {
        String errorEventId = messageProcessingFailedEvent.getIdentity().getId();
        String msg = String.format("Not an Avro message: Unable to parse original message of failed event with ID %s", errorEventId);
        return new NonAvroMessageException(msg, cause);
    }
}
