# MessageProcessingFailedEvent

Every message that cannot be processed must be wrapped into a `MessageProcessingFailedEvent` and published
to the error topic of the system. The jEAP messaging error handler does this automatically for all
consumers; the Error Handling Service consumes these events. The event is itself a regular jEAP domain
event. Its canonical Avro definition lives in the
`jeap-messaging-avro-errorevent` module; see [Avro IDL](#avro-idl).

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

The authoritative [Avro IDL](https://github.com/jeap-admin-ch/jeap-messaging/blob/master/jeap-messaging-avro-errorevent/src/main/avro/MessageProcessingFailedEvent.avdl)
is maintained in `jeap-messaging-avro-errorevent`. Use the schema shipped with the messaging version
of your application; the linked source shows the current repository definition.

## Producing the event

The event is generated and published by the jEAP Messaging error handler. See
[Temporary exceptions](https://jeap-admin-ch.github.io/docs/building-blocks/libraries/jeap-messaging/error-handling#temporary-exceptions)
for automatic framework-exception classification, application-provided error information and resolver
customization. The EHS consumes the resulting temporality to determine its
[retry and escalation flow](message-flows.md#automatic-retry-of-temporary-errors).

## Related

- [Message Flows](message-flows.md) — how the event travels through the system
- [Architecture](architecture.md) — how the event is persisted
