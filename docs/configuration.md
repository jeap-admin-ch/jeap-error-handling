# Configuration

All configuration properties of the Error Handling Service (EHS), grouped by topic. Baseline defaults are
defined in `errorhandlerDefaultProperties.properties` of the service library. A complete example
configuration can be found in the `jme-messaging-error-scs` module of the
[jme-messaging-example](https://git.example.com/jme/jme-messaging-example) project.

## Kafka

The EHS consumes `MessageProcessingFailedEvent`s from the error topic of the system. When Modulith error handling
is enabled, it consumes `ModulithPublicationProcessingFailedEvent`s from a separate topic. The general Kafka and
schema registry access is configured through the standard
[jEAP messaging properties](https://jeap-admin-ch.github.io/docs/jeap-messaging/) (`jeap.messaging.kafka.*`).
The essential EHS-specific settings are:

```yaml
jeap:
  errorhandling:
    # The topic the EHS consumes MessageProcessingFailedEvents from. All Kafka message
    # consumers of the system publish their failures here (jeap.messaging.kafka.errorTopicName).
    topic: "yoursystem-messageprocessing-failed"
    # Optional: the dedicated topic the EHS consumes Modulith publication failures from.
    modulithPublicationProcessingFailedTopic: "yoursystem-modulith-publication-processing-failed"
    # The dead letter topic for failures of the EHS itself, see Operations.
    deadLetterTopicName: "yoursystem-messageprocessing-deadletter"
  messaging:
    kafka:
      systemName: YOURSYSTEMNAME
      serviceName: ${spring.application.name}
```

Note that for the EHS itself, `jeap.messaging.kafka.errorTopicName` always equals the dead letter topic —
the EHS must not publish its own failures to the topic it consumes from.

An EHS instance that handles Modulith failures configures
`jeap.errorhandling.modulithPublicationProcessingFailedTopic`, declares a consumer contract for
`ModulithPublicationProcessingFailedEvent` on that topic, and declares producer contracts for the retry and discard
command topics used by its source services. When the property is not set, the EHS does not create a Modulith failure
consumer. The command topic names are carried in each failure event and persisted by the EHS; no additional command
topic property is required. The EHS also persists the cluster on which it consumed the failure and selects that
cluster's `TransactionalOutbox` for both commands. Every cluster referenced by an open Modulith error must therefore
remain configured until the error has been retried or discarded.

The topic configuration is validated at startup, the EHS refuses to start if:

- `jeap.errorhandling.topic` or `jeap.errorhandling.deadLetterTopicName` is missing,
- `jeap.errorhandling.topic` and `jeap.errorhandling.deadLetterTopicName` name the same topic — the EHS would
  consume its own failures again and the [dead letter reactivation](operations.md#dead-letter-topic) would
  republish messages into the topic it just read from,
- `jeap.errorhandling.modulithPublicationProcessingFailedTopic`, when configured, names either the regular error
  topic or the dead letter topic,
- `jeap.messaging.kafka.errorTopicName` is configured to something other than the dead letter topic, for the
  default cluster or for any cluster in `jeap.messaging.kafka.cluster.*`.

### Retry of temporary EHS failures

If the EHS hits a transient problem while consuming (e.g. the database is briefly unavailable), it retries
the consumption instead of routing the event to the dead letter topic:

| Property                                                | Description                                                                                                | Default |
|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------|---------|
| `jeap.errorhandling.kafka.errorhandling.retry-interval` | Interval between consumption attempts after a transient processing failure (any Spring `Duration` format). | `30s`   |

A custom Spring Kafka `BackOff` bean named `KafkaErrorHandlingConfiguration.BACKOFF_BEAN_NAME` can be
provided for more detailed control, see [Customization](customization.md).

## Frontend and OAuth

The EHS UI is secured with OAuth2/OIDC; the backend is a jEAP OAuth2 resource server
(`jeap.security.oauth2.resourceserver.*`, see the
[jEAP security documentation](https://jeap-admin-ch.github.io/docs/jeap-spring-boot-starters/)).

```yaml
jeap:
  errorhandling:
    frontend:
      client-id: "error-handling-ui"        # OAuth client configured in the identity provider
      system-name: "jme"                    # system name used in the role names
      auto-login: true
      silent-renew: true
      renew-user-info-after-token-renew: true
      application-url: http://localhost:8072/error-handling/
      redirect-url: http://localhost:8072/error-handling/redirect
      logout-redirect-uri: http://localhost:4199/
      mock-pams: true
      pams-environment: REF
  security:
    oauth2:
      resourceserver:
        system-name: "jme"
```

The required user roles are described in [User Interface](user-interface.md#roles).

### PAMS / ePortal

The UI header contains the ePortal service navigation of Oblique, which is backed by PAMS
(`https://pams-api.eportal<environment>.admin.ch`).

| Property           | Description                                                                                                        | Default |
|--------------------|--------------------------------------------------------------------------------------------------------------------|---------|
| `pams-enabled`     | Whether the application is integrated with PAMS/ePortal.                                                             | `true`  |
| `pams-environment` | ePortal environment of the service navigation: `DEV`, `TEST`, `REF`, `ABN` or `PROD`. Required unless PAMS is disabled. | -       |
| `mock-pams`        | Treat the PAMS session as always active instead of reading it from the service navigation. Implied by `pams-enabled: false`. | `false` |

Set `pams-enabled: false` for deployments without PAMS:

```yaml
jeap:
  errorhandling:
    frontend:
      pams-enabled: false
```

The UI then does not contact the ePortal backend at all - no service navigation requests, no ePortal session
timeout handling - and authentication is based solely on OAuth2/OIDC. The header controls served by PAMS
(login/logout, profile, messages) would be non-functional and are hidden; the language selection remains
available.

Note that `pams-environment` must match the environment of the identity provider the UI authenticates
against. Pointing the service navigation at a different environment than the authentication leads to an
inconsistent login state in the header and to logout and timeout redirects into the wrong ePortal.

## Error list view defaults

The default filter settings of the error list view are configurable. A user's locally persisted view
settings (browser local storage) take precedence over these defaults.

| Property                                                 | Description                                                                                           | Default     |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------|-------------|
| `jeap.errorhandling.error-list.default-no-ticket-filter` | Default value of the "no Jira ticket" filter of the error list view.                                  | `false`     |
| `jeap.errorhandling.error-list.default-state-filter`     | Default error state filter of the error list view (`PERMANENT`, `TEMPORARY`, `RETRIED` or `DELETED`). | `PERMANENT` |

## Log deep link

The UI can link from an error directly into the log system, using the trace ID of the failed processing.
The query template must contain the token `{traceId}`.

| Property                 | Description                       | Default                    |
|--------------------------|-----------------------------------|----------------------------|
| `log.deep-link.base-url` | Query template of the log system. | Splunk template, see below |

Example templates:

| Log system       | Query template                                                                                                        |
|------------------|-----------------------------------------------------------------------------------------------------------------------|
| Splunk (example) | `https://splunk.example.com/en-GB/app/search?q=search%20msg.traceId%3D{traceId}%20earliest%3D-1mon` |
| AWS CloudWatch   | CloudWatch Logs Insights URL with a `filter traceId = "{traceId}"` query                                              |

## Resending

Temporary errors are retried by the `DefaultResendingStrategy`:

| Property                                                                          | Description                                                                           | Default |
|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|---------|
| `jeap.errorhandling.resend.default-resend-strategy.delay`                         | (Minimal) delay between receiving a failed message and resending it.                  | `30s`   |
| `jeap.errorhandling.resend.default-resend-strategy.max-retries`                   | Maximum number of resends per failed message; afterwards the error becomes permanent. | `15`    |
| `jeap.errorhandling.resend.default-resend-strategy.exponential-backoff-enabled`   | Whether the delay grows exponentially with each retry.                                | `true`  |
| `jeap.errorhandling.resend.default-resend-strategy.exponential-backoff-factor`    | Multiplier applied to the previous delay for the next resend.                         | `2`     |
| `jeap.errorhandling.resend.default-resend-strategy.exponential-backoff-max-delay` | Upper bound of the delay when exponential back-off is enabled.                        | `1d`    |

A custom `ResendingStrategy` bean can replace the default, see [Customization](customization.md).

### Resend headers

The following Kafka headers are set on resent messages:

| Header                           | Value                                       | Purpose                                                                 |
|----------------------------------|---------------------------------------------|-------------------------------------------------------------------------|
| `jeap_eh_target_service`         | Name of the service whose processing failed | Used by jEAP messaging to filter out messages resent for other services |
| `jeap_eh_error_handling_service` | Name of the resending EHS instance          | Debugging                                                               |

## Agir task management

Permanent errors create a manual task in the Agir task management service (task type `errorhandling`).
For local setups and tests the integration can be disabled — calls to Agir are then only logged.

| Property                                               | Description                                                                                    | Default |
|--------------------------------------------------------|------------------------------------------------------------------------------------------------|---------|
| `jeap.errorhandling.task-management.service.enabled`   | Whether the Agir task management service is integrated.                                        | `true`  |
| `jeap.errorhandling.task-management.service.url`       | URL of the Agir task management service.                                                       | -       |
| `jeap.errorhandling.task-management.service.client-id` | Client id of the Spring Security OAuth2 client registration used to authenticate against Agir. | -       |

The EHS authenticates against Agir with a standard Spring Security OAuth2 client registration
(`spring.security.oauth2.client.*`); the issuer must be the Agir realm.

Task creation is handled by the `DefaultTaskFactory`:

| Property                                                                 | Description                                                          | Default          |
|--------------------------------------------------------------------------|----------------------------------------------------------------------|------------------|
| `jeap.errorhandling.task-management.default-factory.errorServiceBaseUrl` | Base URL of the EHS, used for links from the task back into the EHS. | -                |
| `jeap.errorhandling.task-management.default-factory.priority`            | Priority of the created tasks.                                       | `HIGH`           |
| `jeap.errorhandling.task-management.default-factory.system`              | System the tasks are assigned to.                                    | -                |
| `jeap.errorhandling.task-management.default-factory.timeToHandle`        | Time a user has to handle the task.                                  | `1d`             |
| `jeap.errorhandling.task-management.default-factory.domain`              | Agir domain the task is filed under.                                 | `error-handling` |
| `jeap.errorhandling.task-management.default-factory.taskReferenceName`   | Title of the link back to the EHS.                                   | `Error Service`  |

The task type display in Agir is configurable per language:

```yaml
jeap.errorhandling.task-management.default-factory:
  display:
    DE:
      title: "Fehlgeschlagene Nachrichten-Verarbeitung"
      description: "Ein technischer Fehler ist aufgrund einer unverarbeitbaren Nachricht aufgetreten."
      displayName: "Nachrichten-Verarbeitungsfehler"
      displayDomain: "Error Handling"
    # FR / IT / EN analogous
```

A custom `TaskFactory` bean can replace the default, see [Customization](customization.md).

## Jira issue tracking

The Jira integration for [error groups](error-groups.md) is optional — simply omit the issue tracking and
Jira properties to disable it.

| Property                                                                                   | Description                                                                                                                                         | Default                                                                    |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `jeap.errorhandling.error-groups.issue-tracking.project`                                   | The Jira project in which tickets are created.                                                                                                      | -                                                                          |
| `jeap.errorhandling.error-groups.issue-tracking.issue-type`                                | The type of the created tickets.                                                                                                                    | `Bug`                                                                      |
| `jeap.errorhandling.error-groups.issue-tracking.issue-summary-template`                    | Template for the ticket summary. Supported parameters: `group-id`, `group-created-datetime`, `message-type`, `source`, `error-code`, `error-count`. | `Processing of '{message-type}' from '{source}' fails with '{error-code}'` |
| `jeap.errorhandling.error-groups.issue-tracking.error-handling-service-group-url-template` | URL template linking back to an error group in the EHS, with the `{groupId}` placeholder. Used in the ticket description.                           | -                                                                          |
| `jeap.errorhandling.jira.base-url`                                                         | URL of the Jira instance.                                                                                                                           | -                                                                          |
| `jeap.errorhandling.jira.username`                                                         | User name for the Jira access and/or reporter of the ticket.                                                                                        | -                                                                          |
| `jeap.errorhandling.jira.password`                                                         | Password for basic authentication. May be empty when `token` is set.                                                                                | -                                                                          |
| `jeap.errorhandling.jira.token`                                                            | Token for bearer authentication. Takes precedence over `password`.                                                                                  | -                                                                          |

Example:

```yaml
jeap:
  errorhandling:
    error-groups:
      issue-tracking:
        project: "JME"
        error-handling-service-group-url-template: "${jeap.errorhandling.frontend.application-url}/error-group-details/{groupId}"
    jira:
      base-url: "https://jira.example.com"
      username: "${username.secret.from.secrets.manager}"
      token: "${token.secret.from.secrets.manager}"
```

### Technical user for the Jira integration

Recommended setup for the Jira access:

1. Create a dedicated technical user (S-user) for automated ticket creation.
2. Grant it minimal permissions: create issues only (no read/edit), only on the Jira projects used by the
   EHS instances, plus UI login to create a token.
3. Log in to the Jira UI with the technical user and create a Personal Access Token (with automatic expiry)
   under Profile → Personal Access Tokens.
4. Store the user name and token in the secret store of the platform (Vault or AWS Secrets Manager) and
   reference them in the `jeap.errorhandling.jira.username` / `token` properties.

## Error groups

| Property                                               | Description                                                                  | Default                                           |
|--------------------------------------------------------|------------------------------------------------------------------------------|---------------------------------------------------|
| `jeap.errorhandling.error-groups.errorGroupingEnabled` | Enable or disable the creation of error groups.                              | `true`                                            |
| `jeap.errorhandling.frontend.ticketingSystemUrl`       | URL of a ticket in the ticketing system with a `{ticketNumber}` placeholder. | `https://jira.example.com/browse/{ticketNumber}` |

The default sorting of the error group view is also configurable, see [Error Groups](error-groups.md).

## Housekeeping and metrics

See [Operations](operations.md) for the housekeeping and metrics configuration.

## Related

- [Getting Started](getting-started.md) — setting up an EHS instance
- [Operations](operations.md) — dead letter topic, housekeeping, metrics, multi-cluster
- [Customization](customization.md) — custom resending strategy, task factory and back-off
