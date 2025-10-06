package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters.ProcessingFailedEventConverter;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
class MessageProcessingFailedEventListener implements AcknowledgingMessageListener<Object, SpecificRecordBase> {

    private static final List<Class<? extends Throwable>> RECOVERABLE_EXCEPTIONS = List.of(
            org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.dao.QueryTimeoutException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.hibernate.QueryTimeoutException.class,
            org.hibernate.exception.LockAcquisitionException.class,
            org.hibernate.exception.JDBCConnectionException.class,
            java.sql.SQLTransientException.class,
            java.sql.SQLTransientConnectionException.class);

    private final ProcessingFailedEventConverter processingFailedEventConverter;
    private final ErrorEventHandler errorEventHandler;
    private final String clusterName;

    @Override
    public void onMessage(ConsumerRecord<Object, SpecificRecordBase> data, Acknowledgment acknowledgment) {
        try {
            consume(data.value());
        } catch (Exception e) {
            RuntimeException rte = mapException(e);
            log.error("An error occurred during the processing of a failed message.", rte);
            throw rte;
        }
        acknowledgment.acknowledge();
    }

    /**
     * Due Backward-Compatibility the KafkaListener has to deal with the 'old' EventProcessingFailedEvent from
     * jeap-domain-library and the new MessageProcessingFailedEvent for jeap-messaging-Library.
     * <p>
     * In case of an error, the event will be published in the DLT, which is configured using the property
     * jeap.errorhandling.deadLetterTopicName
     */
    private void consume(SpecificRecordBase errorEvent) {
        MessageProcessingFailedEvent messageProcessingFailedEvent;
        if (errorEvent instanceof EventProcessingFailedEvent) {
            messageProcessingFailedEvent = processingFailedEventConverter.convert((EventProcessingFailedEvent) errorEvent);
        } else {
            messageProcessingFailedEvent = (MessageProcessingFailedEvent) errorEvent;
        }
        errorEventHandler.handle(clusterName, messageProcessingFailedEvent);
    }

    private RuntimeException mapException(Throwable t) {
        if (ExceptionCauseChainChecker.containsCauseType(t, RECOVERABLE_EXCEPTIONS) || isTxOrDbReadOnlyException(t)) {
            return new RecoverableEhsProcessingException(t);
        } else {
            return new FatalEhsProcessingException(t);
        }
    }

    private boolean isTxOrDbReadOnlyException(Throwable t) {
        if (t instanceof DataAccessException dae) {
            Throwable rootCause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(dae);
            // SQL state "25006" -> "READ ONLY SQL TRANSACTION"
            return rootCause instanceof java.sql.SQLException sqlEx && "25006".equals(sqlEx.getSQLState());
        }
        return false;
    }

}
