package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageProcessingFailedEventListenerTest {

    @Mock
    private ErrorEventHandler errorEventHandler;

    @Mock
    private Acknowledgment acknowledgment;

    @Mock
    private MessageProcessingFailedEvent messageProcessingFailedEvent;

    private MessageProcessingFailedEventListener listener;

    private static final String CLUSTER_NAME = "test-cluster";

    @BeforeEach
    void setUp() {
        listener = new MessageProcessingFailedEventListener(errorEventHandler, CLUSTER_NAME);
    }

    @Test
    void onMessage_shouldProcessMessageAndAcknowledgeWhenSuccessful() {
        ConsumerRecord<Object, MessageProcessingFailedEvent> consumerRecord = createConsumerRecord(messageProcessingFailedEvent);

        listener.onMessage(consumerRecord, acknowledgment);

        verify(errorEventHandler).handle(CLUSTER_NAME, messageProcessingFailedEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onMessage_shouldThrowRecoverableExceptionForExceptionFromListOfRecoverableExceptions() {
        ConsumerRecord<Object, MessageProcessingFailedEvent> consumerRecord = createConsumerRecord(messageProcessingFailedEvent);
        doThrow(new DataAccessResourceFailureException("DB connection failed"))
                .when(errorEventHandler).handle(anyString(), eq(messageProcessingFailedEvent));

        RecoverableEhsProcessingException exception = assertThrows(RecoverableEhsProcessingException.class,
                () -> listener.onMessage(consumerRecord, acknowledgment));

        assertNotNull(exception.getCause());
        assertInstanceOf(DataAccessResourceFailureException.class, exception.getCause());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onMessage_shouldThrowRecoverableExceptionWhenRecoverableExceptionNestedInChain() {
        ConsumerRecord<Object, MessageProcessingFailedEvent> consumerRecord = createConsumerRecord(messageProcessingFailedEvent);
        DataAccessResourceFailureException cause = new DataAccessResourceFailureException("DB error");
        RuntimeException wrappedException = new RuntimeException("Wrapper", cause);
        doThrow(wrappedException)
                .when(errorEventHandler).handle(anyString(), eq(messageProcessingFailedEvent));

        RecoverableEhsProcessingException exception = assertThrows(RecoverableEhsProcessingException.class,
                () -> listener.onMessage(consumerRecord, acknowledgment));

        assertNotNull(exception.getCause());
        assertEquals(wrappedException, exception.getCause());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onMessage_shouldThrowRecoverableExceptionForReadOnlyTransactionException() {
        ConsumerRecord<Object, MessageProcessingFailedEvent> consumerRecord = createConsumerRecord(messageProcessingFailedEvent);
        SQLException readOnlySqlException = new SQLException("Transaction is read-only", "25006");
        DataAccessException dataAccessException = new DataAccessResourceFailureException("Read-only transaction", readOnlySqlException);
        doThrow(dataAccessException)
                .when(errorEventHandler).handle(anyString(), eq(messageProcessingFailedEvent));

        RecoverableEhsProcessingException exception = assertThrows(RecoverableEhsProcessingException.class,
                () -> listener.onMessage(consumerRecord, acknowledgment));

        assertNotNull(exception.getCause());
        assertInstanceOf(DataAccessException.class, exception.getCause());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onMessage_shouldThrowFatalExceptionForNonRecoverableException() {
        ConsumerRecord<Object, MessageProcessingFailedEvent> consumerRecord = createConsumerRecord(messageProcessingFailedEvent);
        doThrow(new NullPointerException("Null value"))
                .when(errorEventHandler).handle(anyString(), eq(messageProcessingFailedEvent));

        FatalEhsProcessingException exception = assertThrows(FatalEhsProcessingException.class,
                () -> listener.onMessage(consumerRecord, acknowledgment));

        assertNotNull(exception.getCause());
        assertInstanceOf(NullPointerException.class, exception.getCause());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onMessage_shouldThrowFatalExceptionWhenNoRecoverableNestedInChain() {
        ConsumerRecord<Object, MessageProcessingFailedEvent> consumerRecord = createConsumerRecord(messageProcessingFailedEvent);
        IllegalStateException cause = new IllegalStateException("Invalid state");
        RuntimeException wrappedException = new RuntimeException("Wrapper", cause);
        doThrow(wrappedException)
                .when(errorEventHandler).handle(anyString(), eq(messageProcessingFailedEvent));

        FatalEhsProcessingException exception = assertThrows(FatalEhsProcessingException.class,
                () -> listener.onMessage(consumerRecord, acknowledgment));

        assertNotNull(exception.getCause());
        assertEquals(wrappedException, exception.getCause());
        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<Object, MessageProcessingFailedEvent> createConsumerRecord(MessageProcessingFailedEvent event) {
        return new ConsumerRecord<>("test-topic", 0, 0L, null, event);
    }

}
