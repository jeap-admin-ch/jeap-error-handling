package ch.admin.bit.jeap.errorhandling.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

    // Spring Security 7 method-level authorization (e.g. @PreAuthorize) throws AuthorizationDeniedException
    // instead of letting the framework return 403, so map it here to preserve the API contract.
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

}
