package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.kafka.contract.ContractsValidator;
import ch.admin.bit.jeap.messaging.model.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In the error service we do not want to check for contracts...
 * The error service can read only EventProcessingFailedEvents or MessageProcessFailedEvents
 * and publish everything
 */
@Component
@Slf4j
public class ErrorServiceContractValidator implements ContractsValidator {

    private static final String MESSAGE_FAILED_TYPE = MessageProcessingFailedEvent.getClassSchema().getName();
    private static final String EVENT_FAILED_TYPE = "EventProcessingFailedEvent"; // has been removed from messaging library

    @Override
    public void ensurePublisherContract(MessageType messageType, String topic) {
        // This check will not be executed as the KafkaFailedEventResender disables the default interceptors configured by the
        // messaging library, because those expect messages to be of MessageType which would not allow to resend arbitrary messages.
    }

    @Override
    public void ensureConsumerContract(String messageTypeName, String topic) {
        // The error handler will consume all events, and deliberately fail if an unexpected event is received.
        // If the contract validator was active, the error handler would not consume these events, and they would
        // not be sent to the dead letter topic.
    }

}
