package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.ErrorEventHandler;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEventRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ErrorEventHandlerService implements ErrorEventHandler {
    private final ErrorService errorService;
    private final CausingEventRepository causingEventRepository;
    private final ErrorEventMapper errorEventMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * Do whatever needs to be done for a failed event reported to the error handler by a microservice.
     *
     * @param errorEvent The error for the event which failed to be processed successfully and thus was reported to the error handler.
     */
    @Override
    public void handle(String clusterName, MessageProcessingFailedEvent errorEvent) {
        if (errorService.isEventDuplicate(errorEvent.getIdentity().getIdempotenceId())) {
            log.info("Received an error event with an already handled idempotence ID. Skipping this event: {}.", errorEvent);
            return;
        }

        CausingEvent causingEvent = createOrGetCausingEvent(clusterName, errorEvent);

        Error error = errorEventMapper.toError(errorEvent, causingEvent);
        switch (error.getErrorEventData().getTemporality()) {
            case TEMPORARY:
                errorService.handleTemporaryError(error);
                break;
            case PERMANENT:
                errorService.handlePermanentError(error);
                break;
            default:
                errorService.handleUnknownTemporalityError(error);
        }
    }

    private CausingEvent createOrGetCausingEvent(String clusterName, MessageProcessingFailedEvent errorEvent) {
        CausingEvent causingEvent = errorEventMapper.toCausingEvent(clusterName, errorEvent);
        try {
            return saveOrGetCausingEvent(causingEvent);
        } catch (TransactionException ex) {
            if (ex.contains(DataIntegrityViolationException.class)) {
                // Duplicate event id, saved by concurrent transaction - retry to get existing causing event
                return saveOrGetCausingEvent(causingEvent);
            }
            throw ex;
        }
    }

    private CausingEvent saveOrGetCausingEvent(CausingEvent causingEvent) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Optional<CausingEvent> persistentCausingEventOptional = causingEventRepository.findByCausingEventId(causingEvent.getMetadata().getId());

            if (persistentCausingEventOptional.isEmpty()) {
                log.debug("New causing event: {}.", causingEvent);
                return causingEventRepository.save(causingEvent);
            }
            CausingEvent persistentCausingEvent = persistentCausingEventOptional.get();


            persistentCausingEvent.setMessage(causingEvent.getMessage());
            log.debug("Updated causing event: {}.", persistentCausingEvent);

            return causingEventRepository.save(persistentCausingEvent);
        });
    }

}
