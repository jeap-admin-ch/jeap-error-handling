# Getting Started

This repository is published as a **library**: every business system runs its own instance of the Error
Handling Service (EHS). This page describes how to set up such an instance. Every jEAP application that
consumes messages must have an EHS — a message must never be lost, even when its consumer cannot process
it.

## 1. Create the instance repository

Create a source code repository for your EHS instance containing:

- a POM with the parent `jeap-error-handling-service-instance`:

  ```xml
  <parent>
      <groupId>ch.admin.bit.jeap</groupId>
      <artifactId>jeap-error-handling-service-instance</artifactId>
      <version><!-- use the latest version --></version>
      <relativePath/>
  </parent>
  ```

- configuration files (`application-<env>.yml`), see [Configuration](configuration.md).

An instance can initially be created after the template of the `jme-messaging-error-scs` module in the
[jme-messaging-example](https://git.example.com/jme/jme-messaging-example)
project. Note that this template shows the instantiation inside a multi-module project: it does not use
`jeap-error-handling-service-instance` as parent and therefore adds the `jeap-error-handling-service`
dependency explicitly. In multi-module setups, make sure the `jeap-spring-boot-parent` version of the
project parent matches the one used by the EHS dependency.

## 2. Order the Kafka topics

Two topics are required per system. Modulith error handling adds one shared failure topic and retry and discard command
topics for each source service (see the platform documentation on creating Kafka topics):

| Topic                              | Naming convention                                 | Purpose                                                                                                                                                                |
|------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Message processing failure topic   | `<system>-messageprocessing-failed`               | Kafka message consumers publish `MessageProcessingFailedEvent`s here (`jeap.messaging.kafka.errorTopicName`); the EHS consumes it (`jeap.errorhandling.topic`).        |
| Modulith publication failure topic | `<system>-modulith-publication-processing-failed` | Services using the Modulith starter publish `ModulithPublicationProcessingFailedEvent`s here; configure `jeap.errorhandling.modulithPublicationProcessingFailedTopic`. |
| Modulith retry command topic       | `<system>-<service>-retry-modulith-publication`   | The EHS publishes retry commands here; configure `jeap.modulith.error-handling.retry-command-topic` in the source service.                                            |
| Modulith discard command topic     | `<system>-<service>-discard-modulith-publication` | The EHS publishes discard commands here; configure `jeap.modulith.error-handling.discard-command-topic` in the source service.                                        |
| Dead letter topic                  | `<system>-messageprocessing-deadletter`           | Failures of the EHS itself (`jeap.errorhandling.deadLetterTopicName`), see [Operations](operations.md#dead-letter-topic).                                              |

The regular failure, Modulith failure, and dead letter topics configured in the EHS must be distinct. The EHS checks
these three properties at startup and refuses to start otherwise, as it must not consume its own failures again and
each inbound topic carries exactly one event type. Retry and discard topics must likewise not overlap topics carrying
another message type.

The Kafka user of the EHS needs read access to both configured failure topics, write access to the dead letter and
Modulith command topics, and **write access to every topic the EHS must be able to resend messages to**.

For Modulith onboarding, reuse the system's existing EHS with Modulith support. The application publishes failure
events; the EHS consumes them and publishes retry/discard commands back to that application. Permissions must be
granted separately to the application and EHS Kafka identities. No additional command-topic properties are needed
in EHS: the failure event supplies the reply destinations. Roll out the configuration and restart EHS to activate
the failure listener before testing.

The starter's [Kafka topics and service responsibilities](https://github.com/jeap-admin-ch/jeap-spring-modulith-error-handling-starter/blob/main/docs/getting-started.md#kafka-topics-and-service-responsibilities)
guide lists the producers, consumers, topic properties, contracts and permissions on each side, together with signing
requirements and a first end-to-end check. Internal Modulith events do not themselves require Kafka topics.

## 3. Configure the consumers of your system

Enable the jEAP messaging error handling in every service that consumes messages: configure
`jeap.messaging.kafka.errorTopicName` to the error topic. The jEAP messaging error handler then wraps
processing failures into [MessageProcessingFailedEvents](message-processing-failed-event.md) automatically.
Classify failures that are worth retrying as temporary by throwing exceptions that implement
`MessageHandlerExceptionInformation`.

## 4. Configure the EHS instance

Minimal configuration of the instance (see [Configuration](configuration.md) for everything else):

```yaml
jeap:
  errorhandling:
    topic: "yoursystem-messageprocessing-failed"
    # Required only when this EHS handles failed Modulith publications
    modulithPublicationProcessingFailedTopic: "yoursystem-modulith-publication-processing-failed"
    deadLetterTopicName: "yoursystem-messageprocessing-deadletter"
    frontend:
      client-id: "error-handling-ui"
      system-name: "yoursystem"
      application-url: https://yoursystem.example.com/error-handling/
  messaging:
    kafka:
      systemName: YOURSYSTEM
      serviceName: ${spring.application.name}
```

The EHS instance needs no message contract annotations for the Modulith publication failure event or its retry and
discard commands. Its existing `ErrorServiceContractValidator` deliberately performs no contract checks.
The source microservice using the Modulith error handling starter must, however, declare consumer contracts for
`RetryModulithPublicationCommand` and `DiscardModulithPublicationCommand` on its configured command topics. The enabled
starter validates these contracts at startup using the microservice's normal validator. The failure event does not
need a producer contract in the source microservice because jEAP Messaging exempts that framework-owned event.

## 5. Set up authorization

Create the OAuth client for the UI in the identity provider and assign the user roles described in
[User Interface](user-interface.md#roles) (`<systemname>_@error_#view` / `#retry` / `#delete` and the
`errorgroup` roles).

## 6. Connect the task management (optional but recommended)

Order access to Agir, create a client in the Agir realm and configure the
[task management integration](configuration.md#agir-task-management) so that permanent errors create
manual tasks. For local development the integration can be disabled
(`jeap.errorhandling.task-management.service.enabled=false`).

## 7. Connect Jira (optional)

Configure the [Jira issue tracking integration](configuration.md#jira-issue-tracking) to create and link
tickets on [error groups](error-groups.md).

## Next steps

- [Architecture](architecture.md) — how the EHS works
- [Message Flows](message-flows.md) — runtime behaviour
- [Configuration](configuration.md) — the complete property reference
- [Development](development.md) — working on this repository itself
