# jEAP Error Handling Service

The jEAP Error Handling Service (EHS) makes sure that no Kafka message is lost when its processing fails in
a business application. It consumes `MessageProcessingFailedEvent`s published by the jEAP messaging error
handler, persists the failed messages, automatically retries temporary errors, escalates permanent errors
to manual tasks, and provides a UI to inspect, resend and close errors — with optional grouping of related
errors and Jira issue tracking.

This repository is published as a **library**: every business system creates and deploys its own EHS
instance depending on it.

## Documentation

- [Getting Started](docs/getting-started.md) — set up an EHS instance for your system
- [Architecture](docs/architecture.md) — context, building blocks, error state model, data model
- [Message Flows](docs/message-flows.md) — sequence diagrams of the runtime behaviour
- [MessageProcessingFailedEvent](docs/message-processing-failed-event.md) — the inbound event contract
- [Configuration](docs/configuration.md) — the complete property reference
- [User Interface](docs/user-interface.md) — views, filters, roles and persisted view settings
- [Error Groups](docs/error-groups.md) — grouping of permanent errors and Jira integration
- [Operations](docs/operations.md) — dead letter topic, housekeeping, metrics, multi-cluster
- [Customization](docs/customization.md) — custom resending strategy, task factory and back-off
- [Development](docs/development.md) — building, running and testing this repository

## Versioning

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part of the open source distribution of jEAP. See
[github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap) for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
