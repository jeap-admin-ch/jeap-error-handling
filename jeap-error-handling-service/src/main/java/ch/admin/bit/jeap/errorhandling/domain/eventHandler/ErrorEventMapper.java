package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializerProvider;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData.Temporality;
import ch.admin.bit.jeap.messaging.avro.errorevent.ErrorTypeReference;
import ch.admin.bit.jeap.messaging.avro.errorevent.FailedMessageMetadata;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedPayload;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContext;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
class ErrorEventMapper {

    private static final String EVENT_ID = "eventId";
    private final TraceContextProvider traceContextProvider;
    private final Map<String, Deserializer<GenericData.Record>> deserializersByClusterName;

    ErrorEventMapper(DomainEventDeserializerProvider deserializerFactory,
                     TraceContextProvider traceContextProvider,
                     KafkaProperties kafkaProperties) {
        this.deserializersByClusterName = kafkaProperties.clusterNames().stream()
                .collect(Collectors.toMap(clusterName -> clusterName,
                        deserializerFactory::getGenericRecordDomainEventDeserializer));
        this.traceContextProvider = traceContextProvider;
    }

    CausingEvent toCausingEvent(String clusterName, MessageProcessingFailedEvent errorEvent) {
        return CausingEvent.builder()
                .metadata(extractCausingEventMetadata(clusterName, errorEvent))
                .message(extractCausingEventMessage(clusterName, errorEvent))
                .headers(extractCausingEventHeaders(errorEvent))
                .build();
    }

    private List<MessageHeader> extractCausingEventHeaders(MessageProcessingFailedEvent errorEvent) {
        Optional<FailedMessageMetadata> optionalFailedMessageMetadata =
                errorEvent.getPayload().getOptionalFailedMessageMetadata();

        if (optionalFailedMessageMetadata.isEmpty()) {
            return List.of();
        }
        Map<String, ByteBuffer> headers = optionalFailedMessageMetadata.get().getHeaders();
        if (headers == null) {
            return List.of();
        }

        return headers.entrySet().stream()
                .map(entry -> MessageHeader.builder()
                        .headerName(entry.getKey())
                        .headerValue(entry.getValue().array())
                        .build())
                .toList();
    }

    Error toError(MessageProcessingFailedEvent errorEvent, CausingEvent causingEvent) {
        return Error.builder().
                state(initialState(errorEvent)).
                causingEvent(causingEvent).
                errorEventData(extractErrorEventData(errorEvent)).
                errorEventMetadata(extractErrorEventMetadata(errorEvent)).
                created(ZonedDateTime.now()).
                closingReason("").
                originalTraceContext(retrieveTraceContextFromCurrentTraceContext()).
                build();
    }

    private OriginalTraceContext retrieveTraceContextFromCurrentTraceContext() {
        try {
            final TraceContext traceContext = traceContextProvider.getTraceContext();

            return OriginalTraceContext.builder()
                    .traceIdHigh(traceContext.getTraceIdHigh())
                    .traceId(traceContext.getTraceId())
                    .spanId(traceContext.getSpanId())
                    .parentSpanId(traceContext.getParentSpanId())
                    .traceIdString(traceContext.getTraceIdString()).build();

        } catch (Exception e) {
            log.error("Error retrieving current trace context. Returning null.", e);
            return null;
        }
    }

    private ErrorState initialState(MessageProcessingFailedEvent errorEvent) {
        Temporality temporality = Temporality.valueOf(errorEvent.getReferences().getErrorType().getTemporality());
        return temporality == Temporality.TEMPORARY ? ErrorState.TEMPORARY_RETRY_PENDING : ErrorState.PERMANENT;
    }

    private EventMetadata extractCausingEventMetadata(String clusterName, MessageProcessingFailedEvent errorEvent) {
        Optional<FailedMessageMetadata> optionalFailedMessageMetadata =
                errorEvent.getPayload().getOptionalFailedMessageMetadata();

        return optionalFailedMessageMetadata.map(this::createEventMetadataFromFailedEvent)
                .orElseGet(() -> createEventMetadataByDeserializingCausingEvent(clusterName, errorEvent));
    }

    private EventMetadata createEventMetadataByDeserializingCausingEvent(String clusterName, MessageProcessingFailedEvent errorEvent) {
        String causingMessageTopic = errorEvent.getReferences().getMessage().getTopicName();
        GenericData.Record causingEvent = tryDeserializeIfAvro(errorEvent, clusterName, causingMessageTopic);
        EventMetadata.EventMetadataBuilder eventMetadataBuilder = EventMetadata.builder();

        GenericData.Record identity = (GenericData.Record) causingEvent.get("identity");
        String messageId = getMessageId(identity);
        eventMetadataBuilder.
                id(messageId).
                idempotenceId(toString(identity.get("idempotenceId"))).
                created(ZonedDateTime.ofInstant(Instant.ofEpochMilli((Long) identity.get("created")), ZoneId.of("UTC")));

        GenericData.Record publisher = (GenericData.Record) causingEvent.get("publisher");
        eventMetadataBuilder.publisher(EventPublisher.builder().
                system(toString(publisher.get("system"))).
                service(toString(publisher.get("service"))).build());

        GenericData.Record type = (GenericData.Record) causingEvent.get("type");
        eventMetadataBuilder.type(EventType.builder().
                name(toString(type.get("name"))).
                version(toString(type.get("version"))).build());

        return eventMetadataBuilder.build();
    }

    private EventMetadata createEventMetadataFromFailedEvent(FailedMessageMetadata failedMessageMetadata) {
        EventMetadata.EventMetadataBuilder eventMetadataBuilder = EventMetadata.builder();

        eventMetadataBuilder.
                id(failedMessageMetadata.getEventId()).
                idempotenceId(failedMessageMetadata.getIdempotenceId()).
                created(ZonedDateTime.ofInstant(failedMessageMetadata.getCreated(), ZoneId.of("UTC")));

        eventMetadataBuilder.publisher(EventPublisher.builder().
                system(failedMessageMetadata.getSystem()).
                service(failedMessageMetadata.getService()).build());

        eventMetadataBuilder.type(EventType.builder().
                name(failedMessageMetadata.getMessageTypeName()).
                version(failedMessageMetadata.getMessageTypeVersion()).build());

        return eventMetadataBuilder.build();
    }

    private GenericData.Record tryDeserializeIfAvro(MessageProcessingFailedEvent messageProcessingFailedEvent,
                                                    String clusterName,
                                                    String causingMessageTopic) {
        byte[] causingMessagePayload = messageProcessingFailedEvent.getPayload().getOriginalMessage().array();
        try {
            Deserializer<GenericData.Record> avroDeserializer = deserializersByClusterName.get(clusterName);
            return avroDeserializer.deserialize(causingMessageTopic, causingMessagePayload);
        } catch (Exception ex) {
            throw NonAvroMessageException.of(messageProcessingFailedEvent, ex);
        }
    }

    private String getMessageId(GenericData.Record identity) {
        boolean hasEventId = identity.getSchema().getField(EVENT_ID) != null;
        return hasEventId ? toString(identity.get(EVENT_ID)) : toString(identity.get("id"));
    }

    private EventMetadata extractErrorEventMetadata(MessageProcessingFailedEvent errorEvent) {
        AvroDomainEventIdentity srcEventIdentity = errorEvent.getIdentity();
        AvroDomainEventType srcEventTyp = errorEvent.getType();
        AvroDomainEventPublisher srcEventPublisher = errorEvent.getPublisher();
        return EventMetadata.builder().
                id(srcEventIdentity.getEventId()).
                idempotenceId(srcEventIdentity.getIdempotenceId()).
                created(srcEventIdentity.getCreatedZoned()).
                type(EventType.builder().
                        name(srcEventTyp.getName()).
                        version(srcEventTyp.getVersion()).
                        build()).
                publisher(EventPublisher.builder().
                        service(srcEventPublisher.getService()).
                        system(srcEventPublisher.getSystem()).
                        build()).
                build();
    }

    private EventMessage extractCausingEventMessage(String clusterName, MessageProcessingFailedEvent errorEvent) {
        return EventMessage.builder().
                payload(errorEvent.getPayload().getOriginalMessage().array()).
                key(errorEvent.getPayload().getOriginalKey() == null ? null : errorEvent.getPayload().getOriginalKey().array()).
                topic(errorEvent.getReferences().getMessage().getTopicName()).
                clusterName(clusterName).
                partition(Long.parseLong(errorEvent.getReferences().getMessage().getPartition())).
                offset(Long.parseLong(errorEvent.getReferences().getMessage().getOffset())).
                build();
    }

    private ErrorEventData extractErrorEventData(MessageProcessingFailedEvent errorEvent) {
        ErrorTypeReference errorTypeReference = errorEvent.getReferences().getErrorType();
        MessageProcessingFailedPayload errorPayload = errorEvent.getPayload();
        return ErrorEventData.builder().
                code(errorTypeReference.getCode()).
                temporality(Temporality.valueOf(errorTypeReference.getTemporality())).
                message(replaceNullCharWithBlank(errorPayload.getErrorMessage())).
                description(errorPayload.getErrorDescription()).
                stackTrace(replaceNullCharWithBlank(errorPayload.getStackTrace())).
                stackTraceHash(errorPayload.getStackTraceHash()).
                build();
    }

    /**
     * Postgres cannot store null chars as text. Replacing the null chars with blanks.
     *
     * @param message the original string
     * @return the string without null chars
     */
    private String replaceNullCharWithBlank(String message) {
        if (message == null) {
            return null;
        }
        return message.replace('\u0000', ' ');
    }

    @PreDestroy
    private void closeKafkaAvroDeserializers() {
        deserializersByClusterName.values().forEach(Deserializer::close);
    }

    // If there is no "avro.java.string":"String" hint on avro string types in the avro schema used by the reader
    // the kafka avro deserializer will return avro string types as java CharSequence implementations (e.g. Utf8).
    // The avro.java.string hint will be "missing" if the schema has not been created by a specific avro type built by the
    // avro maven plugin or if the option avro.remove.java.properties is set to true in the kafka avro deserializer config.
    private String toString(Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof CharSequence cs) {
            return cs.toString();
        } else {
            throw new IllegalArgumentException("Expected a CharSequence instance (i.e. an avro string), got " + o.getClass().getName() + " instead.");
        }
    }
}
