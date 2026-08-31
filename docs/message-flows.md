# Message Flows

This page shows the runtime behaviour of the Error Handling Service (EHS) as sequence diagrams. See
[Architecture](architecture.md) for the static structure and the error state model.

## Failed message intake

When message processing fails in a business service, the jEAP messaging error handler publishes a
`MessageProcessingFailedEvent` to the error topic and acknowledges the original message, so the consumer
is not blocked. The EHS consumes the failed event, persists the error together with the original message
bytes, and classifies it.

```mermaid
sequenceDiagram
    autonumber
    participant Consumer as Business service<br/>(jEAP messaging error handler)
    participant ErrorTopic as error topic
    participant EHS as Error Handling Service
    participant DB as Database
    participant Agir as Agir task management

    Consumer->>Consumer: message processing throws exception
    Consumer->>ErrorTopic: publish MessageProcessingFailedEvent
    Consumer->>Consumer: acknowledge original message
    ErrorTopic->>EHS: consume MessageProcessingFailedEvent
    EHS->>DB: persist Error + CausingEvent (original bytes)
    alt temporary failure and retries left
        EHS->>EHS: ResendingStrategy determines resend time
        EHS->>DB: state = TEMPORARY_RETRY_PENDING,<br/>schedule resend
    else permanent failure or retries exhausted
        EHS->>DB: state = SEND_TO_MANUALTASK,<br/>assign to error group
        EHS->>Agir: create manual task
        EHS->>DB: state = PERMANENT
    end
```

If the EHS itself fails to process the failed event, the classification in
`MessageProcessingFailedEventListener` decides between an in-place retry and the dead letter topic:

- transient problems (database unavailable, locks, read-only transactions) trigger a Kafka retry with a
  configurable back-off (`RecoverableEhsProcessingException`),
- fatal problems route the event to the dead letter topic (`FatalEhsProcessingException`), see
  [Operations](operations.md#dead-letter-topic).

## Failed Modulith publication intake

A service using the Modulith error handling starter publishes a
`ModulithPublicationProcessingFailedEvent` after the publication exhausts its local retry budget. The EHS
persists the publication ID, listener, internal event payload, consumed Kafka cluster, and the source service's retry
and discard command topics as a permanent error. A manual retry queues `RetryModulithPublicationCommand`; closing the
error queues `DiscardModulithPublicationCommand`. Both commands are inserted into the transactional outbox for that
same Kafka cluster in the same transaction as the EHS state change. If the cluster is no longer configured, the action
fails and the EHS error remains open rather than silently sending the command through the default cluster.

## Automatic retry of temporary errors

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as ResendScheduler<br/>(ShedLock)
    participant DB as Database
    participant EHS as KafkaFailedEventResender
    participant Topic as original topic
    participant Consumer as Business service

    loop configured cron interval
        Scheduler->>DB: load due ScheduledResends
        Scheduler->>EHS: resend causing event
        EHS->>Topic: republish original message bytes<br/>(headers jeap_eh_target_service, jeap_eh_error_handling_service)
        EHS->>DB: state = TEMPORARY_RETRIED
    end
    Topic->>Consumer: consume message again
    alt processing succeeds
        Consumer->>Consumer: business process continues
    else processing fails again
        Consumer->>EHS: new MessageProcessingFailedEvent
        Note over EHS: new Error for the same causing event.<br/>The ResendingStrategy escalates to a permanent<br/>error once max-retries is reached
    end
```

The message is republished to the cluster it was originally consumed from (see
[Operations](operations.md#multi-cluster-support)). The header `jeap_eh_target_service` allows other
consumers of the same topic to ignore messages that are resent for a different service.

## Manual retry and delete from the UI

Operators with the corresponding roles can manually resend or close permanent errors in the UI. Both
actions are recorded in the audit log with the acting user from the JWT token. Kafka errors resend the
stored causing message. Modulith errors publish a UUID-exact command through the transactional outbox.

```mermaid
sequenceDiagram
    autonumber
    actor Operator
    participant UI as EHS UI
    participant EHS as Error Handling Service
    participant Topic as original topic
    participant Outbox as transactional outbox
    participant CommandTopic as Modulith command topic<br/>(failure event cluster)
    participant Starter as Modulith error handling starter
    participant Agir as Agir task management
    participant Sync as TasksSynchronize
    participant DB as Database

    alt manual retry (role error:retry)
        Operator->>UI: retry error
        UI->>EHS: POST /api/error/:errorId/event/retry
        alt Kafka message origin
            EHS->>Topic: republish causing message
            EHS->>DB: state = RESOLVE_ON_MANUALTASK
            EHS->>Agir: close manual task
            EHS->>DB: state = PERMANENT_RETRIED, audit log entry
        else Modulith publication origin
            EHS->>Outbox: queue RetryModulithPublicationCommand
            EHS->>DB: state = RESOLVE_ON_MANUALTASK, audit log entry
            Note over EHS,DB: outbox entry and state commit atomically
            par asynchronous command delivery
                Outbox->>CommandTopic: publish command
                CommandTopic->>Starter: retry publicationId
            and asynchronous task synchronization
                Sync->>Agir: close manual task after commit
                Sync->>DB: state = PERMANENT_RETRIED
            end
        end
    else delete / ignore (role error:delete)
        Operator->>UI: delete error with closing reason
        UI->>EHS: DELETE /api/error/:errorId
        alt Modulith publication origin
            EHS->>Outbox: queue DiscardModulithPublicationCommand
            EHS->>DB: state = DELETE_ON_MANUALTASK, audit log entry
            Note over EHS,DB: outbox entry and state commit atomically
            par asynchronous command delivery
                Outbox->>CommandTopic: publish command
                CommandTopic->>Starter: complete publicationId
            and asynchronous task synchronization
                Sync->>Agir: close manual task after commit
                Sync->>DB: state = DELETED
            end
        else Kafka message origin
            EHS->>DB: state = DELETE_ON_MANUALTASK
            EHS->>Agir: close manual task
            EHS->>DB: state = DELETED, audit log entry
        end
    end
```

For Modulith publications, the command references the ID of the consumed publication failure event. If command
enqueueing fails, the database transaction rolls back and the error remains open. If Agir is unavailable, the state
remains at `RESOLVE_ON_MANUALTASK` / `DELETE_ON_MANUALTASK` and a later `TasksSynchronize` run retries the close.

## Error handling of the Error Handling Service itself

So that no message is lost even when the EHS fails, the EHS uses the regular jEAP messaging error handling
for its own consumption — but publishing to its own error topic would create a loop. The EHS therefore
publishes its own processing failures to a dedicated dead letter topic:

```mermaid
sequenceDiagram
    autonumber
    participant ErrorTopic as error topic
    participant EHS as Error Handling Service
    participant DLT as dead letter topic

    ErrorTopic->>EHS: consume MessageProcessingFailedEvent
    alt transient EHS problem (e.g. database down)
        EHS->>EHS: retry consumption with back-off<br/>(does not commit the offset)
    else fatal EHS problem
        EHS->>DLT: publish failed event to the dead letter topic
        Note over DLT: monitored by alerting -<br/>should always be empty
    end
```

## Related

- [Architecture](architecture.md) — error state model and data model
- [Configuration](configuration.md#resending) — resend strategy and retry properties
- [Operations](operations.md) — dead letter topic monitoring, housekeeping, metrics
