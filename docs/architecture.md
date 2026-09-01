# Architecture

The jEAP Error Handling Service (EHS) is a self-contained system that handles failed Kafka messages and
Spring Modulith event publications. Business services never retry failed Kafka messages
themselves: the [jEAP messaging error handler](https://jeap-admin-ch.github.io/docs/jeap-messaging/) wraps the
failed message into a `MessageProcessingFailedEvent` and publishes it to an error topic. The EHS consumes
that topic, persists the failure, retries temporary errors, escalates permanent errors to manual tasks, and
offers a UI to inspect, resend and close errors. Services using the Modulith error handling starter report
exhausted publications to a separate Modulith publication failure topic and receive retry or discard commands from
the EHS.

## Goals and constraints

- **No message is ever lost.** Every message that cannot be processed ends up either persisted in the EHS or,
  if even the EHS cannot process it, on a dedicated dead letter topic.
- **One EHS instance per business system.** The EHS is published as a library; every system creates its own
  deployable instance (see [Getting Started](getting-started.md)).
- **Retries are the responsibility of the EHS**, not of the consuming services. Consumers classify failures
  as temporary or permanent; the EHS schedules resends for temporary failures.

## Context

```mermaid
flowchart LR
  Producer["Message producer"]
  Topic[/"Business topic"/]
  Consumer["Business service<br/>(jEAP messaging error handler)"]
  ModulithService["Business service<br/>(Modulith error handling starter)"]

  subgraph ERROR_HANDLING["Error handling"]
    direction TB
    ErrorTopic[/"Error topic"/]
    ModulithErrorTopic[/"Modulith publication<br/>failure topic"/]
    EHS["Error Handling Service"]
    DB[("PostgreSQL")]
    UI["Angular UI<br/>(bundled)"]
    DLT[/"Dead letter topic"/]
    ErrorTopic --> EHS
    ModulithErrorTopic --> EHS
    UI --- EHS
    EHS --> DB
    EHS -->|" Failed events that<br/>cannot be processed "| DLT
  end

  subgraph EXTERNAL["External systems"]
    direction TB
    Agir["Agir task management"]
    Jira["Jira issue tracking<br/>(optional)"]
  end

  Producer -->|" Business messages "| Topic
  Topic --> Consumer
  Consumer -->|" MessageProcessingFailedEvent "| ErrorTopic
  ModulithService -->|" ModulithPublicationProcessingFailedEvent "| ModulithErrorTopic
  EHS -->|" Resend original message "| Topic
  EHS -->|" Create Agir manual tasks for permanent errors "| Agir
  EHS -->|" Create JIRA issue or link tickets "| Jira
```

## Building blocks

| Module                                 | Description                                                                                                                              |
|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `jeap-error-handling-service`          | Spring Boot backend with all domain and infrastructure logic, published as a library. The built Angular UI is served from its classpath. |
| `jeap-error-handling-ui`               | Angular frontend. Built with npm/`ng build` during the Maven build and packaged as static resources.                                     |
| `jeap-error-handling-service-instance` | Thin `pom`-packaging parent used by the per-system instance repositories.                                                                |

Inside `jeap-error-handling-service` the main components are:

```mermaid
flowchart TB
    subgraph inbound["infrastructure/kafka"]
        Listener["MessageProcessingFailedEventListener"]
        ModulithListener["ModulithPublicationProcessingFailedEventListener"]
        Resender["KafkaFailedEventResender"]
    end
    subgraph domain["domain"]
        Handler["ErrorEventHandler / ErrorFactory"]
        Service["ErrorService<br/>(transactional orchestrator)"]
        Strategy["ResendingStrategy<br/>(DefaultResendingStrategy)"]
        Scheduler["ResendScheduler<br/>(ShedLock)"]
        Tasks["TaskFactory / TasksSynchronize"]
        Groups["ErrorGroupService"]
        Housekeeping["HouseKeepingService"]
        Metrics["ErrorHandlingMetricsService"]
    end
    subgraph outbound["infrastructure"]
        Persistence[("persistence<br/>Error, CausingEvent, ...")]
        TaskClient["TaskManagementClient (Agir)"]
        JiraClient["Jira client"]
    end
    Web["web/api REST controllers + web/ui"]

    Listener --> Handler --> Service
    ModulithListener --> Handler
    Service --> Strategy
    Service --> Persistence
    Scheduler --> Resender
    Scheduler --> Persistence
    Service --> Tasks --> TaskClient
    Service --> Groups --> JiraClient
    Web --> Service
    Housekeeping --> Persistence
    Metrics --> Persistence
```

## Error state model

The central entity is the `Error` with its `ErrorState`, which drives the entire lifecycle. Temporary
failures are retried automatically; permanent failures create a manual task and wait for an operator.

```mermaid
stateDiagram-v2
    [*] --> TEMPORARY_RETRY_PENDING: temporary failure,<br/>resend scheduled
    [*] --> SEND_TO_MANUALTASK: permanent failure or<br/>retries exhausted
    TEMPORARY_RETRY_PENDING --> TEMPORARY_RETRIED: causing message resent
    TEMPORARY_RETRY_PENDING --> DELETED: deleted by operator<br/>(resend cancelled)
    SEND_TO_MANUALTASK --> PERMANENT: manual task created
    SEND_TO_MANUALTASK --> PERMANENT_RETRIED: resent before task creation
    SEND_TO_MANUALTASK --> DELETED: deleted before task creation
    PERMANENT --> RESOLVE_ON_MANUALTASK: causing message resent<br/>by operator
    RESOLVE_ON_MANUALTASK --> PERMANENT_RETRIED: manual task closed
    PERMANENT --> DELETE_ON_MANUALTASK: deleted by operator
    DELETE_ON_MANUALTASK --> DELETED: manual task deleted
    TEMPORARY_RETRIED --> [*]
    PERMANENT_RETRIED --> [*]
    DELETED --> [*]
```

A message whose processing fails again after a resend simply produces a new `MessageProcessingFailedEvent`,
i.e. a new `Error` for the same causing event. The `ResendingStrategy` sees the error count per causing event
and escalates a temporary error to a permanent one once the maximum number of retries is reached.

The intermediate states `SEND_TO_MANUALTASK`, `RESOLVE_ON_MANUALTASK` and `DELETE_ON_MANUALTASK` decouple the
state changes from the availability of the task management service: if Agir cannot be reached, the scheduled
`TasksSynchronize` job picks the errors up later and completes the transition.

For a manual retry or discard of a Modulith publication, the command outbox entry and the corresponding
`RESOLVE_ON_MANUALTASK` or `DELETE_ON_MANUALTASK` state are committed in one database transaction. The external
manual task is closed only by a later `TasksSynchronize` run, after that transaction has committed. Kafka-origin
actions retain their synchronous manual-task close attempt.

## Data model

```mermaid
erDiagram
    ERROR ||--|| CAUSING_EVENT : "caused by"
    ERROR }o--o| ERROR_GROUP : "grouped into"
    ERROR ||--o{ AUDIT_LOG : "audited by"
    ERROR ||--o{ SCHEDULED_RESEND : "resent by"

    ERROR {
        uuid id PK
        string state "ErrorState"
        string error_code
        string error_message
        string temporality "PERMANENT or TEMPORARY"
        string stack_trace
        string stack_trace_hash
        string closing_reason
        string manual_task_id
        string trace_id "original trace context"
        timestamp created
        timestamp modified
    }
    CAUSING_EVENT {
        uuid id PK
        string event_id "metadata of the causing message"
        string idempotence_id
        string message_type
        string publisher_system
        string publisher_service
        string topic
        string cluster_name
        long partition
        long offset
        bytes key
        bytes payload "original message bytes"
    }
    ERROR_GROUP {
        uuid id PK
        string error_code
        string event_name
        string error_publisher
        string error_message
        string error_stack_trace_hash
        string ticket_number "Jira ticket"
        string free_text
        timestamp created
        timestamp modified
    }
    AUDIT_LOG {
        uuid id PK
        uuid error_id FK
        string action "RESEND_CAUSING_EVENT or DELETE_ERROR"
        string auth_context "user from the JWT"
        string subject
        string given_name
        string family_name
        timestamp created
    }
    SCHEDULED_RESEND {
        uuid id PK
        uuid error_id FK
        timestamp resend_at
        timestamp resent_at
        boolean cancelled
    }
```

The causing message is stored exactly as it was read from Kafka (key and payload as byte arrays), so it can
be republished unchanged, even if it could not be deserialized in the first place.

## Deployment view

The EHS runs as a standard jEAP Spring Boot microservice. All scheduled jobs — `ResendScheduler`,
`HouseKeepingScheduler`, `TasksSynchronize` and the metrics sampling — use ShedLock with a JDBC lock
provider, so multiple instances can run in parallel and each job executes on exactly one instance.

Production uses PostgreSQL with Flyway migrations; integration tests run against H2.

## Related

- [Message Flows](message-flows.md) — sequence diagrams of the runtime behaviour
- [MessageProcessingFailedEvent](message-processing-failed-event.md) — the inbound event contract
- [Configuration](configuration.md) — all configuration properties
- [Error Groups](error-groups.md) — grouping of permanent errors and Jira integration
