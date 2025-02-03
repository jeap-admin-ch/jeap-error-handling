package ch.admin.bit.jeap.errorhandling.domain.exceptions;

public class TicketNumberAlreadyExistsException extends RuntimeException {
    public TicketNumberAlreadyExistsException(String ticketNumber) {
        super(ticketNumber);
    }
}