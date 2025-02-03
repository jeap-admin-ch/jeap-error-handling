package ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters;

import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedPayload;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedPayload;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ProcessingFailedPayloadConverter implements Converter<EventProcessingFailedPayload, MessageProcessingFailedPayload> {

    @Override
    public MessageProcessingFailedPayload convert(EventProcessingFailedPayload eventProcessingFailedPayload) {
        MessageProcessingFailedPayload messageProcessingFailedPayload = MessageProcessingFailedPayload.newBuilder()
                .setErrorMessage(eventProcessingFailedPayload.getErrorMessage())
                .setErrorDescription(eventProcessingFailedPayload.getErrorDescription())
                .setOriginalKey(eventProcessingFailedPayload.getOriginalKey())
                .setOriginalMessage(eventProcessingFailedPayload.getOriginalMessage())
                .setStackTrace(eventProcessingFailedPayload.getStackTrace())
                .setFailedMessageMetadata(null)
                .build();
        return messageProcessingFailedPayload;
    }
}
