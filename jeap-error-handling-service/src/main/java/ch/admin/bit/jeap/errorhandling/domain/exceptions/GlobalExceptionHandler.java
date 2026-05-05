package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ErrorGroupNotFoundException.class,
                       ErrorGroupAlreadyHasATicketNumberAssignedException.class,
                       InvalidUuidException.class,
                       IssueTrackingBadRequest.class})
    public ResponseEntity<ErrorResponse> handleAsBadRequestWithErrorMessage(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({IssueTrackingCommunicationError.class, IssueTrackingServerError.class})
    public ResponseEntity<ErrorResponse> handleIssueTrackingError(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_GATEWAY.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_GATEWAY);
    }

}
