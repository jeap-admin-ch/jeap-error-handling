package ch.admin.bit.jeap.errorhandling.util;

import ch.admin.bit.jeap.domainevent.DomainEvent;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedEvent;
import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedPayload;
import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedReferences;
import ch.admin.bit.jeap.messaging.avro.AvroMessageBuilderException;
import ch.admin.bit.jeap.messaging.avro.SerializedMessageHolder;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Due Backward-Compatibility we need this Builder to create the EventProcessingFailedEvent in the IT-Tests
 */
@Slf4j
@SuppressWarnings({"findbugs:SS_SHOULD_BE_STATIC"})
public class EventProcessingFailedEventBuilder extends AvroDomainEventBuilder<EventProcessingFailedEventBuilder, EventProcessingFailedEvent> {
    @Getter
    private final String eventName = "EventProcessingFailedEvent";
    @Getter
    private final String specifiedMessageTypeVersion = "1.0.0";
    @Getter
    private String serviceName;
    @Getter
    private String systemName;

    private ConsumerRecord<?, ?> originalMessage;
    private MessageHandlerExceptionInformation eventHandlerExceptionInformation;
    private String processId = null;

    private EventProcessingFailedEventBuilder() {
        super(EventProcessingFailedEvent::new);
    }

    public static EventProcessingFailedEventBuilder create() {
        return new EventProcessingFailedEventBuilder();
    }

    public EventProcessingFailedEventBuilder originalMessage(ConsumerRecord<?, ?> originalMessage) {
        this.originalMessage = originalMessage;

        if (originalMessage.value() instanceof DomainEvent) {
            this.processId = ((DomainEvent) originalMessage.value())
                    .getOptionalProcessId()
                    .orElse(null);
        }
        return self();
    }

    public EventProcessingFailedEventBuilder eventHandleException(MessageHandlerExceptionInformation eventHandlerExceptionInformation) {
        this.eventHandlerExceptionInformation = eventHandlerExceptionInformation;
        return self();
    }

    public EventProcessingFailedEventBuilder systemName(String systemName) {
        this.systemName = systemName;
        return self();
    }

    public EventProcessingFailedEventBuilder serviceName(String serviceName) {
        this.serviceName = serviceName;
        return self();
    }

    @Override
    protected EventProcessingFailedEventBuilder self() {
        return this;
    }

    @Override
    public EventProcessingFailedEvent build() {
        if (this.originalMessage == null) {
            throw AvroMessageBuilderException.propertyNull("errorReferences.message");
        }
        if (this.eventHandlerExceptionInformation == null) {
            throw AvroMessageBuilderException.propertyNull("errorReferences.exception");
        }

        ch.admin.bit.jeap.domainevent.avro.error.ErrorTypeReference errorType = ch.admin.bit.jeap.domainevent.avro.error.ErrorTypeReference.newBuilder()
                .setCode(eventHandlerExceptionInformation.getErrorCode())
                .setTemporality(eventHandlerExceptionInformation.getTemporality().toString())
                .setType("exception")
                .build();
        ch.admin.bit.jeap.domainevent.avro.error.MessageReference messageReference = ch.admin.bit.jeap.domainevent.avro.error.MessageReference.newBuilder()
                .setOffset(String.valueOf(originalMessage.offset()))
                .setPartition(String.valueOf(originalMessage.partition()))
                .setTopicName(originalMessage.topic())
                .setType("message")
                .build();
        EventProcessingFailedReferences errorReferences = EventProcessingFailedReferences.newBuilder()
                .setMessage(messageReference)
                .setErrorType(errorType)
                .build();
        EventProcessingFailedPayload errorPayload = EventProcessingFailedPayload.newBuilder()
                .setErrorMessage(eventHandlerExceptionInformation.getMessage())
                .setOriginalKey(getSerializedMessage(originalMessage.key()))
                .setOriginalMessage(getSerializedMessage(originalMessage.value()))
                .setStackTrace(eventHandlerExceptionInformation.getStackTraceAsString())
                .setErrorDescription(eventHandlerExceptionInformation.getDescription())
                .build();
        setPayload(errorPayload);
        setReferences(errorReferences);
        setProcessId(this.processId);
        // Usually, UUIDs are bad idempotence IDs. However, each processing failure of an event is a unique occurrence
        // of the failure, UUIDs are thus valid in this case.
        idempotenceId(UUID.randomUUID().toString());
        return super.build();
    }

    private ByteBuffer getSerializedMessage(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof SerializedMessageHolder smh) {
            return ByteBuffer.wrap(smh.getSerializedMessage());
        }
        if (obj instanceof byte[]) {
            return ByteBuffer.wrap((byte[]) obj);
        }
        log.error("Could not get serialized message from type " + obj.getClass());
        return null;
    }
}
