package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.contract.ContractsValidator;
import ch.admin.bit.jeap.messaging.model.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In the error service we do not want to validate the contracts.
 */
@Component
@Slf4j
public class ErrorServiceContractValidator implements ContractsValidator {

    @Override
    public void ensurePublisherContract(MessageType messageType, String topic) {
        // This check will not be executed as the KafkaFailedEventResender disables the default interceptors configured by the
        // messaging library, as those expect messages to be of MessageType which would not allow to resend arbitrary messages.
    }

    @Override
    public void ensureConsumerContract(String messageTypeName, String topic) {
        // The error handler will consume all events, and deliberately fail if an unexpected event is received.
        // If the contract validator was active, the error handler would not consume these events, and they would
        // not be sent to the dead letter topic.
    }

}
