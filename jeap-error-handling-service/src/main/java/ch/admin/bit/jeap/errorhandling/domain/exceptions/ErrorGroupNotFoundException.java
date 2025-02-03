package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import java.util.UUID;

public class ErrorGroupNotFoundException extends RuntimeException {
    public ErrorGroupNotFoundException(UUID errorGroupId) {
        super(String.format("ErrorGroup with UUID %s not found", errorGroupId));
    }
}
