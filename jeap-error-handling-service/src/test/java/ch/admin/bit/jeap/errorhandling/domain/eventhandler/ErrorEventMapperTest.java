package ch.admin.bit.jeap.errorhandling.domain.eventhandler;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializerProvider;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.OriginalTraceContext;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContext;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorEventMapperTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void retrieveTraceContextFromCurrentTraceContext_persistsSampledFlag(boolean sampled) {
        TraceContextProvider provider = mock(TraceContextProvider.class);
        when(provider.getTraceContext()).thenReturn(
                new TraceContext(1L, 2L, 3L, 4L, "00000000000000010000000000000002", sampled));
        ErrorEventMapper mapper = new ErrorEventMapper(mock(DomainEventDeserializerProvider.class), provider, mock(KafkaProperties.class));

        OriginalTraceContext result = mapper.retrieveTraceContextFromCurrentTraceContext();

        assertThat(result.getSampled())
                .as("Upstream sampling decision must be persisted so error resend does not re-sample.")
                .isEqualTo(sampled);
    }

    @Test
    void retrieveTraceContextFromCurrentTraceContext_returnsNull_whenNoTraceContextActive() {
        TraceContextProvider provider = mock(TraceContextProvider.class);
        when(provider.getTraceContext()).thenReturn(null);
        ErrorEventMapper mapper = new ErrorEventMapper(mock(DomainEventDeserializerProvider.class), provider, mock(KafkaProperties.class));

        OriginalTraceContext result = mapper.retrieveTraceContextFromCurrentTraceContext();

        assertThat(result).isNull();
    }

    @Test
    void sanitize_stringWithNullChar_returnStringWithBlank() {
        final char nullChar = '\u0000';
        final String result = ErrorEventTextSanitizer.sanitize("myString" + nullChar + "Test" + nullChar);
        assertThat(result).isEqualTo("myString Test ");
    }

    @Test
    void sanitize_stringIsNull_returnNull() {
        assertThat(ErrorEventTextSanitizer.sanitize(null)).isNull();
    }

}
