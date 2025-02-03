package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializerProvider;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ErrorEventMapperTest {

    @Test
    void replaceNullCharWithBlank_stringWithNullChar_returnStringWithBlank() throws Exception {
        ErrorEventMapper errorEventMapper = new ErrorEventMapper(mock(DomainEventDeserializerProvider.class), null, mock(KafkaProperties.class));
        final char nullChar = '\u0000';
        final String result = (String) getReplaceNullCharWithBlankMethod().invoke(errorEventMapper, "myString" + nullChar + "Test" + nullChar);
        assertThat(result).isEqualTo("myString Test ");
    }

    @Test
    void replaceNullCharWithBlank_stringIsNull_returnNull() throws Exception {
        ErrorEventMapper errorEventMapper = new ErrorEventMapper(mock(DomainEventDeserializerProvider.class), null, mock(KafkaProperties.class));
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
