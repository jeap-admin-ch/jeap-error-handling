package ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters;

import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedReferences;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedReferences;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessingFailedReferencesConverter implements Converter<EventProcessingFailedReferences, MessageProcessingFailedReferences> {

    private final ErrorTypesReferencesConverter errorTypesReferencesConverter;
    private final MessageReferencesConverter messageReferencesConverter;

    @Override
    public MessageProcessingFailedReferences convert(EventProcessingFailedReferences eventProcessingFailedReferences) {
        return MessageProcessingFailedReferences.newBuilder()
                .setErrorType(errorTypesReferencesConverter.convert(eventProcessingFailedReferences.getErrorType()))
                .setMessage(messageReferencesConverter.convert(eventProcessingFailedReferences.getMessage()))
                .build();
    }
}
