package ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MessageReferencesConverter
        implements Converter<ch.admin.bit.jeap.domainevent.avro.error.MessageReference, MessageReference> {

    @Override
    public MessageReference convert(ch.admin.bit.jeap.domainevent.avro.error.MessageReference messageReference) {
        return MessageReference.newBuilder()
                .setOffset(messageReference.getOffset())
                .setType(messageReference.getType())
                .setPartition(messageReference.getPartition())
                .setTopicName(messageReference.getTopicName())
                .build();
    }
}
