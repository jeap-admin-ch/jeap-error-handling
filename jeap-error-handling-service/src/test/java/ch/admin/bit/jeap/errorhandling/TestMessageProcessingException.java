package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import lombok.Getter;

@Getter
public class TestMessageProcessingException extends RuntimeException implements MessageHandlerExceptionInformation {

    private final String errorCode;
    private final Temporality temporality;
    private final String message;
    private final String description = "";
    private final String stackTraceAsString = "";

    public TestMessageProcessingException(Temporality temporality, String errorCode, String message) {
        super(message);
        this.temporality = temporality;
        this.errorCode = errorCode;
        this.message = message;
    }

}
