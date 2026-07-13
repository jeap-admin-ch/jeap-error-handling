# MessageProcessingFailedEvent

Every message that cannot be processed must be wrapped into a `MessageProcessingFailedEvent` and published
to the error topic of the system. The jEAP messaging error handler does this automatically for all
consumers; the Error Handling Service consumes these events. The event is itself a regular jEAP domain
event. Its canonical Avro definition lives in the
[jeap-messaging](https://jeap-admin-ch.github.io/docs/jeap-messaging/) repository
(`jeap-messaging-avro-errorevent`).

## Content

The event references:

- the message that caused the failure, given by Kafka topic, partition and offset, and
- the error case, given by an application-specific error code and a temporality
  (`PERMANENT`, `TEMPORARY` or unknown).

Its payload contains:

- the value of the failing message (byte array, exactly as stored in Kafka),
- the key of the failing message (byte array, optional),
- an error message,
- an optional general error description (free text, e.g. hints or links),
- the Java stack trace as string (optional),
- a hash of the stack trace (optional, used for [error grouping](error-groups.md)), and
- optional metadata of the failed message (ids, timestamps, system/service, message type, headers).

Because key and value are transported as raw bytes, the original message is preserved even if it could not
be deserialized (e.g. an invalid message on the topic) — and the Error Handling Service can republish it
unchanged on retry.

## Avro IDL

```text
@namespace("ch.admin.bit.jeap.messaging.avro.errorevent")
protocol MessageProcessingFailedEventProtocol {
    import idl "DomainEventBaseTypes.avdl";

    record MessageProcessingFailedPayload {
        bytes originalMessage;
        union{null, bytes} originalKey = null;
        string errorMessage;
        union{null, string} errorDescription = null;
        union{null, string} stackTrace = null;
        union{null, string} stackTraceHash = null;
        union{null, FailedMessageMetadata} failedMessageMetadata = null;
    }

    record FailedMessageMetadata {
        union{null, string} eventId = null;
        union{null, string} idempotenceId = null;
        union{null, timestamp_ms} created = null;
        union{null, string} system = null;
        union{null, string} service = null;
        union{null, string} messageTypeName = null;
        union{null, string} messageTypeVersion = null;
        map<bytes> headers = {};
    }

    record MessageProcessingFailedReferences {
        MessageReference message;
        ErrorTypeReference errorType;
    }

    record MessageReference {
        string type;
        string topicName;
        string partition;
        string offset;
    }

    record ErrorTypeReference {
        string type;
        string temporality;
        string code;
    }

    record MessageProcessingFailedEvent {
        ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity identity;
        ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType type;
        ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher publisher;
        MessageProcessingFailedReferences references;
        MessageProcessingFailedPayload payload;
        union{null, string} processId = null;
        string domainEventVersion;
    }
}
```

## Producing the event

The event is generated automatically by the jEAP messaging error handler and published to the configured
error topic. If the thrown exception implements
`ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation` (or extends the provided
`MessageHandlerException`), the error information — in particular the temporality that decides between
retry and manual task — is taken from the exception. By default, failures are treated as permanent; it is
the responsibility of the business application to classify failures as temporary where a retry makes sense.

## Related

- [Message Flows](message-flows.md) — how the event travels through the system
- [Architecture](architecture.md) — how the event is persisted
