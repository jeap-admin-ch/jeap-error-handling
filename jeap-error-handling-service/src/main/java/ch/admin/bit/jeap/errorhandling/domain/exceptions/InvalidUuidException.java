package ch.admin.bit.jeap.errorhandling.domain.exceptions;

public class InvalidUuidException extends RuntimeException {
    public InvalidUuidException(String invalidUuid) {
        super(String.format("Not a valid UUID: '%s'", invalidUuid));
    }
}
