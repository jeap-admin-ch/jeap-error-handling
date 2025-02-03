package ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters;

import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessingFailedEventConverter implements Converter<EventProcessingFailedEvent, MessageProcessingFailedEvent> {

    private final ProcessingFailedPayloadConverter payloadConverter;
    private final ProcessingFailedReferencesConverter referencesConverter;

    @Override
    public MessageProcessingFailedEvent convert(EventProcessingFailedEvent event) {
        return MessageProcessingFailedEvent.newBuilder()
                .setDomainEventVersion(event.getDomainEventVersion())
                .setIdentity(event.getIdentity())
                .setPayload(payloadConverter.convert(event.getPayload()))
                .setProcessId(event.getProcessId())
                .setPublisher(event.getPublisher())
                .setReferences(referencesConverter.convert(event.getReferences()))
                .setType(event.getType())
                .build();
    }
}
