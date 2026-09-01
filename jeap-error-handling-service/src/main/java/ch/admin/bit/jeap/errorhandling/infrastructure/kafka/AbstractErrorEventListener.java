package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

@Slf4j
abstract class AbstractErrorEventListener<T> implements AcknowledgingMessageListener<Object, T> {

    private static final List<Class<? extends Throwable>> RECOVERABLE_EXCEPTIONS = List.of(
            org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.dao.QueryTimeoutException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.hibernate.QueryTimeoutException.class,
            org.hibernate.exception.LockAcquisitionException.class,
            org.hibernate.exception.JDBCConnectionException.class,
            java.sql.SQLTransientException.class,
            java.sql.SQLTransientConnectionException.class);

    protected final ErrorEventHandler errorEventHandler;
    protected final String clusterName;
    private final Class<T> eventType;

    AbstractErrorEventListener(ErrorEventHandler errorEventHandler, String clusterName, Class<T> eventType) {
        this.errorEventHandler = errorEventHandler;
        this.clusterName = clusterName;
        this.eventType = eventType;
    }

    @Override
    public final void onMessage(ConsumerRecord<Object, T> data, Acknowledgment acknowledgment) {
        try {
            Object value = data.value();
            if (!eventType.isInstance(value)) {
                throw new IllegalArgumentException("Unsupported error event type: " + value.getClass().getName());
            }
            consume(eventType.cast(value));
        } catch (Exception e) {
            RuntimeException rte = mapException(e);
            log.error("An error occurred during the processing of a failed event.", rte);
            throw rte;
        }
        acknowledgment.acknowledge();
    }

    protected abstract void consume(T errorEvent);

    private RuntimeException mapException(Throwable t) {
        if (ExceptionCauseChainChecker.containsCauseType(t, RECOVERABLE_EXCEPTIONS) || isTxOrDbReadOnlyException(t)) {
            return new RecoverableEhsProcessingException(t);
        }
        return new FatalEhsProcessingException(t);
    }

    private boolean isTxOrDbReadOnlyException(Throwable t) {
        if (t instanceof DataAccessException dae) {
            Throwable rootCause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(dae);
            return rootCause instanceof java.sql.SQLException sqlEx && "25006".equals(sqlEx.getSQLState());
        }
        return false;
    }
}
