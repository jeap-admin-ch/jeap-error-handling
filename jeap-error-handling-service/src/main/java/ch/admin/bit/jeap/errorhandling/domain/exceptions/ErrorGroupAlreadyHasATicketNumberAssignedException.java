package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import java.util.UUID;

public class ErrorGroupAlreadyHasATicketNumberAssignedException extends RuntimeException {
    public ErrorGroupAlreadyHasATicketNumberAssignedException(UUID errorGroupId, String alreadyAssignedTicketNumber) {
        super(String.format("ErrorGroup with UUID %s already has a ticket number assigned: %s.", errorGroupId, alreadyAssignedTicketNumber));
    }
}
