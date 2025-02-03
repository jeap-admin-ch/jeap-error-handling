package ch.admin.bit.jeap.errorhandling.domain.error;

import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.TaskFactory;
import ch.admin.bit.jeap.errorhandling.domain.metrics.ErrorHandlingMetricsService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.domain.resend.strategy.ResendingStrategy;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaFailedEventResender;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskDto;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementClient;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementException;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ScheduledResend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ErrorService {
    private final ErrorRepository errorRepository;
    private final ScheduledResendService scheduledResendService;
    private final KafkaFailedEventResender failedEventResender;
    private final TaskManagementClient taskManagementClient;
    private final TaskFactory taskFactory;
    private final ResendingStrategy resendingStrategy;
    private final ErrorFactory errorFactory;
    private final ErrorHandlingMetricsService errorHandlingMetricsService;
    private final AuditLogService auditLogService;
    private final ErrorGroupService errorGroupService;

    private final String NOT_RETRYABLE = "Error is not in retryable state: ";
    private final String MANUAL_RESEND_NOT_ALLOWED = "Error is not in state to be resend to manual task: ";

    public void handleTemporaryError(Error error) {
        int errorCountForEvent = getErrorCountForCausingEvent(error.getCausingEventMetadata().getId());
        Optional<ZonedDateTime> resendRequest = resendingStrategy.determineResend(errorCountForEvent, error.getCausingEventMetadata(), error.getErrorEventMetadata(), error.getErrorEventData(), error.getCausingEventMessage());
        if (resendRequest.isEmpty()) {
            log.debug("Stop resending temporary error");
            handlePermanentError(error);
        } else {
            error = createTemporary(error, resendRequest.get());
            log.info("Schedule resend for temporary error {}", error);
        }
    }

    public void handlePermanentError(Error error) {
        error = createPermanent(error);
        log.info("Saved permanent error {}", error);
    }

    public void handleUnknownTemporalityError(Error error) {
        log.debug("Handling an error of unknown error temporality as permanent failure.");
        handlePermanentError(error);
    }

    @Transactional(readOnly = true)
    public Error getError(UUID errorId) {
        return errorRepository.getReferenceById(errorId);
    }

    @Transactional(readOnly = true)
    public int getErrorCountForCausingEvent(String causingEventId) {
        return errorRepository.countErrorsForCausingEvent(causingEventId);
    }

    @Transactional(readOnly = true)
    public ErrorList getPermanentErrorList(int pageIndex, int pageSize) {
        Page<Error> errors = errorRepository.findAllPermanent(PageRequest.of(pageIndex, pageSize));
        return new ErrorList(errors.getTotalElements(), errors.getContent());
    }

    @Transactional(readOnly = true)
    public ErrorList getTemporaryErrorList(int pageIndex, int pageSize) {
        return getErrorListByState(ErrorState.TEMPORARY_RETRY_PENDING, pageIndex, pageSize);
    }

    @Transactional(readOnly = true)
    public ErrorList getErrorListByState(ErrorState errorState, int pageIndex, int pageSize) {
        Page<Error> errors = errorRepository.findAllByStateEqualsOrderByCreatedDesc(errorState, PageRequest.of(pageIndex, pageSize));
        return new ErrorList(errors.getTotalElements(), errors.getContent());
    }

    @Transactional(readOnly = true)
    public boolean isEventDuplicate(String errorEventIdempotenceId) {
        return errorRepository.countErrorsByErrorEventIdempotenceId(errorEventIdempotenceId) > 0;
    }

    public void manualResend(UUID errorId) {
        log.debug("Handling retry request for error {}", errorId);
        Error error = getError(errorId);
        if (!error.getState().isRetryAllowed()) {
            throw new IllegalStateException(NOT_RETRYABLE + error.getState());
        }
        failedEventResender.resend(error);
        auditLogService.logResendCausingEvent(error);
        if (error.getState() == ErrorState.TEMPORARY_RETRY_PENDING) {
            scheduledResendService.cancelScheduledResends(error);
        }
        setRetried(error);
        log.info("Resend causing event for error {}", error);
    }

    public void scheduledResend(ScheduledResend scheduledResend) {
        UUID errorId = scheduledResend.getErrorId();
        Error error = getError(errorId);

        try {
            resendEventForTemporaryError(error);
            markErrorAsRetried(scheduledResend, error);
        } catch (Exception ex) {
            log.error("Failed to resend event for error {}", scheduledResend.getErrorId(), ex);
            markErrorAsRetried(scheduledResend, error);
            handleTemporaryErrorResendFailure(error);
        }

        log.info("Resend attempt finished for causing event of error {}", error);
    }

    private void resendEventForTemporaryError(Error error) {
        log.debug("Try resending error {}", error);
        if (!error.getState().equals(ErrorState.TEMPORARY_RETRY_PENDING)) {
            throw new IllegalStateException(NOT_RETRYABLE + error.getState());
        }
        failedEventResender.resend(error);
    }

    private void markErrorAsRetried(ScheduledResend scheduledResend, Error error) {
        setRetried(error);
        scheduledResendService.setResent(scheduledResend);
    }

    private void handleTemporaryErrorResendFailure(Error resentError) {
        // Create a new error, then determine if it needs to be resent or classified as permanent
        Error error = errorFactory.newTemporaryErrorFromTemplate(resentError);
        handleTemporaryError(error);
    }

    public void delete(UUID errorId, String reason) {
        log.info("Handling delete request for error {}", errorId);
        Error error = errorRepository.getReferenceById(errorId);
        if (reason != null && !reason.isBlank()) {
            if (reason.length() > 1000) {
                throw new IllegalArgumentException("Reason must be under 1000 characters");
            }
            error.setClosingReason(reason);
        }

        ErrorState state = error.getState();
        switch (state) {
            case PERMANENT:
                error.setState(ErrorState.DELETE_ON_MANUALTASK);
                deleteManualTask(error);
                break;
            case TEMPORARY_RETRY_PENDING:
                scheduledResendService.cancelScheduledResends(error);
                error.setState(ErrorState.DELETED);
                break;
            case SEND_TO_MANUALTASK:
                error.setState(ErrorState.DELETED);
                break;
            default:
                throw new IllegalStateException("Error is not in deletable state: " + error.getState());
        }
        auditLogService.logDeleteError(error);
    }


    Error createPermanent(Error error) {
        error.setState(ErrorState.SEND_TO_MANUALTASK);
        createManualTask(error);
        String causingService = error.getErrorEventMetadata().getPublisher().getService();
        errorHandlingMetricsService.incrementPermanentCounter(causingService);
        errorGroupService.assignToErrorGroup(error);
        return errorRepository.save(error);
    }

    Error createTemporary(Error error, ZonedDateTime resentAt) {
        error.setState(ErrorState.TEMPORARY_RETRY_PENDING);
        UUID id = error.getId();
        error = errorRepository.save(error);
        errorHandlingMetricsService.incrementTemporaryCounter();
        scheduledResendService.scheduleResend(id, resentAt);
        return error;
    }

    private void setRetried(Error error) {
        ErrorState state = error.getState();
        ErrorEventData eventData = error.getErrorEventData();
        ErrorEventData.Temporality temporality = eventData != null ? eventData.getTemporality() : null;

        switch (state) {
            case TEMPORARY_RETRY_PENDING:
                error.setState(ErrorState.TEMPORARY_RETRIED);
                break;
            case PERMANENT:
                error.setState(ErrorState.RESOLVE_ON_MANUALTASK);
                closeManualTask(error);
                break;
            case SEND_TO_MANUALTASK:
                error.setState(ErrorState.PERMANENT_RETRIED);
                break;
            default:
                switch (temporality) {
                    case PERMANENT:
                        error.setState(ErrorState.PERMANENT_RETRIED);
                        break;
                    case TEMPORARY:
                        error.setState(ErrorState.TEMPORARY_RETRIED);
                        break;
                    default:
                        throw new IllegalStateException(NOT_RETRYABLE + error.getState());
                }
        }
    }

    public void createManualTask(Error error) {
        if (error.getState() != ErrorState.SEND_TO_MANUALTASK) {
            throw new IllegalStateException(MANUAL_RESEND_NOT_ALLOWED + error.getState());
        }
        try {
            TaskDto taskDto = taskFactory.create(error);
            taskManagementClient.createTask(taskDto);
            error.setManualTaskId(taskDto.getId());
            error.setState(ErrorState.PERMANENT);
            errorRepository.save(error);
        } catch (TaskManagementException e) {
            log.warn("Could not send task to manual task service, retry later", e);
        }
    }

    public void deleteManualTask(Error error) {
        if (error.getState() != ErrorState.DELETE_ON_MANUALTASK) {
            throw new IllegalStateException(MANUAL_RESEND_NOT_ALLOWED + error.getState());
        }
        try {
            taskManagementClient.closeTask(error.getManualTaskId());
            error.setState(ErrorState.DELETED);
            errorRepository.save(error);
        } catch (TaskManagementException e) {
            log.warn("Could not close task to manual task service, retry later", e);
        }
    }

    public void closeManualTask(Error error) {
        if (error.getState() != ErrorState.RESOLVE_ON_MANUALTASK) {
            throw new IllegalStateException(MANUAL_RESEND_NOT_ALLOWED + error.getState());
        }
        try {
            taskManagementClient.closeTask(error.getManualTaskId());
            error.setState(ErrorState.PERMANENT_RETRIED);
            errorRepository.save(error);
        } catch (TaskManagementException e) {
            log.warn("Could not close task to manual task service, retry later", e);
        }
    }
}
