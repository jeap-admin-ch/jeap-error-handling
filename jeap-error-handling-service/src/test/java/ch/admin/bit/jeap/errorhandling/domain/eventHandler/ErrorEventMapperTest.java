package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializerProvider;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.OriginalTraceContext;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContext;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
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
    void replaceNullCharWithBlank_stringWithNullChar_returnStringWithBlank() throws Exception {
        ErrorEventMapper errorEventMapper = new ErrorEventMapper(mock(DomainEventDeserializerProvider.class), mock(TraceContextProvider.class), mock(KafkaProperties.class));
        final char nullChar = '\u0000';
        final String result = (String) getReplaceNullCharWithBlankMethod().invoke(errorEventMapper, "myString" + nullChar + "Test" + nullChar);
        assertThat(result).isEqualTo("myString Test ");
    }

    @Test
    void replaceNullCharWithBlank_stringIsNull_returnNull() throws Exception {
        ErrorEventMapper errorEventMapper = new ErrorEventMapper(mock(DomainEventDeserializerProvider.class), mock(TraceContextProvider.class), mock(KafkaProperties.class));
        final String myString = null;
        final String result = (String) getReplaceNullCharWithBlankMethod().invoke(errorEventMapper, myString);
        assertThat(result).isNull();
    }

    private Method getReplaceNullCharWithBlankMethod() throws NoSuchMethodException {
        final Method method = ErrorEventMapper.class.getDeclaredMethod("replaceNullCharWithBlank", String.class);
        method.setAccessible(true);
        return method;
    }

}
