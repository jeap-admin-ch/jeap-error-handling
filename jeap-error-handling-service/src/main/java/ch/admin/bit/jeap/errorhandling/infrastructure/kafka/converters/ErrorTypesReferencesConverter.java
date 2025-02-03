package ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters;

import ch.admin.bit.jeap.messaging.avro.errorevent.ErrorTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ErrorTypesReferencesConverter implements Converter<ch.admin.bit.jeap.domainevent.avro.error.ErrorTypeReference,
        ErrorTypeReference> {

    @Override
    public ErrorTypeReference convert(ch.admin.bit.jeap.domainevent.avro.error.ErrorTypeReference errorTypeReference) {
        return ErrorTypeReference.newBuilder()
                .setCode(errorTypeReference.getCode())
                .setType(errorTypeReference.getType())
                .setTemporality(errorTypeReference.getTemporality())
                .build();
    }
}
