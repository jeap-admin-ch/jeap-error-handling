package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
public class ErrorList {
    long totalElements;
    List<Error> errors;
}
