package ch.admin.bit.jeap.errorhandling.domain.eventhandler;

import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.ErrorEventHandler;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEventRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
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
    private final ModulithErrorEventMapper modulithErrorEventMapper;
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

    @Override
    public void handle(String clusterName, ModulithPublicationProcessingFailedEvent errorEvent) {
        if (errorService.isEventDuplicate(errorEvent.getIdentity().getIdempotenceId())) {
            log.info("Received an error event with an already handled idempotence ID. Skipping this event: {}.", errorEvent);
            return;
        }
        CausingEvent causingEvent = createOrGetCausingEvent(clusterName, errorEvent);

        Error error = modulithErrorEventMapper.toError(errorEvent, causingEvent);
        // The temporality of a failed Modulith publication mirrors the temporality of a MessageProcessingFailedEvent:
        // a temporary failure is retried automatically by sending a retry command to the publishing application,
        // anything else has to be dealt with manually. The Avro enum only knows TEMPORARY and PERMANENT, and an
        // unknown symbol read from a newer schema resolves to PERMANENT, so there is no unknown temporality here.
        if (error.getErrorEventData().getTemporality() == ErrorEventData.Temporality.TEMPORARY) {
            errorService.handleTemporaryError(error);
        } else {
            errorService.handlePermanentError(error);
        }
    }

    private CausingEvent createOrGetCausingEvent(String clusterName,
            ModulithPublicationProcessingFailedEvent errorEvent) {
        CausingEvent causingEvent = modulithErrorEventMapper.toCausingEvent(clusterName, errorEvent);
        try {
            return saveOrGetCausingEvent(causingEvent);
        } catch (TransactionException ex) {
            if (ex.contains(DataIntegrityViolationException.class)) {
                return saveOrGetCausingEvent(causingEvent);
            }
            throw ex;
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
            // If the causing event already exists, we update it with the latest information
            // While this is not necessary usually, it might be required in certain migration cases (new heders,
            // new message format due to cluster migrations with different binary record formats, etc)
            // As there is no way to determine whether an update is strictly necessary, it is always performed.
            if (causingEvent.getOrigin() == CausingEvent.Origin.MODULITH_PUBLICATION) {
                persistentCausingEvent.update(causingEvent.getMetadata(), causingEvent.getModulithPublication());
            } else {
                persistentCausingEvent.update(causingEvent.getMetadata(), causingEvent.getMessage(), causingEvent.getHeaders());
            }
            log.debug("Updated causing event: {}.", persistentCausingEvent);

            return causingEventRepository.save(persistentCausingEvent);
        });
    }
}
