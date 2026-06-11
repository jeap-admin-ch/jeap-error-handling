# AGENTS.md

This file provides guidance for AI coding agents when working with code in this repository.

## What this is

jEAP Error Handling Service (EHS) — a Self-Contained System that consumes `MessageProcessingFailedEvent`s from Kafka, persists the failed messages, retries temporary errors, escalates permanent errors to manual tasks / a UI, and can resend the original (causing) event back to its topic. This repo is published as an open-source **library**; downstream teams build their own error service instance depending on it. External contributions are not accepted (see `CONTRIBUTING.md`).

## Modules

- `jeap-error-handling-service` — the Spring Boot backend (all Java domain + infrastructure logic). Packaged as a jar consumed by service instances.
- `jeap-error-handling-ui` — Angular 21 frontend, built by Maven via npm/`ng build` and bundled into the service jar as static resources.
- `jeap-error-handling-service-instance` — a thin `pom`-packaging module that depends on the service; the deployable/runnable artifact. License plugins are deliberately disabled here.

## Build & test

```bash
# Full build (also runs npm ci + ng build for the UI module on first build)
./mvnw clean install

# Backend only, skip tests
./mvnw -pl jeap-error-handling-service install -DskipTests

# Single backend test class / method
./mvnw -pl jeap-error-handling-service test -Dtest=ErrorServiceTest
./mvnw -pl jeap-error-handling-service test -Dtest=ErrorServiceTest#methodName

# Integration tests (*IT.java) run via failsafe during `verify`/`install`
./mvnw -pl jeap-error-handling-service verify
```

UI (run inside `jeap-error-handling-ui/`):

```bash
npm ci
npm start            # ng serve on localhost:4200 (dev)
npm test             # Jest
npm run lint
npm run build        # ng build with base-href /error-handling/
```

If npm builds fail due to the npm or node version, use nvm to switch to the latest installed LTS node version.  

Integration tests use **H2** (migrations in `src/test/resources/db/migration/h2`) and an embedded Kafka/test infra; the Spring test context cache is capped at size 1 (`spring.test.context.cache.maxSize=1`) to limit memory. Production runs on **PostgreSQL** with Flyway migrations in `src/main/resources/db/migration`.

## Running locally

See `README.md`: publish a local snapshot, then drive it from the `jme-messaging-example` project (Docker stack + OAuth-Mock server). Start the service under profile `local-ui` and run the UI with `ng serve` against it.

## Architecture / core flow

The central entity is `infrastructure/persistence/Error.java` and its `ErrorState` enum, which drives the entire lifecycle. States carry `deleteAllowed` / `retryAllowed` flags:
`TEMPORARY_RETRY_PENDING`, `SEND_TO_MANUALTASK`, `PERMANENT`, `TEMPORARY_RETRIED`, `RESOLVE_ON_MANUALTASK`, `PERMANENT_RETRIED`, `DELETE_ON_MANUALTASK`, `DELETED`.

Inbound path (`infrastructure/kafka`):
1. `MessageProcessingFailedEventListener` consumes the failed-event from Kafka. It classifies thrown exceptions into `RecoverableEhsProcessingException` (transient DB/lock/read-only — triggers Kafka retry) vs `FatalEhsProcessingException` (routed to the dead-letter topic, configured by `jeap.errorhandling.deadLetterTopicName`). See `ExceptionCauseChainChecker`.
2. `ErrorEventHandler` → `ErrorFactory` builds and persists the `Error` plus its `CausingEvent`.

Decision/retry path (`domain`):
- `ErrorService` is the transactional orchestrator. `handleTemporaryError` asks the `ResendingStrategy` (`domain/resend/strategy`, `DefaultResendingStrategy`) whether/when to retry based on retry count; either schedules a resend or escalates to a permanent error.
- `domain/resend/scheduler/ResendScheduler` (ShedLock `@Scheduled`) picks up due `ScheduledResend` rows and uses `KafkaFailedEventResender` to republish the original causing event to its cluster/topic (`ResendClusterProvider`).
- Permanent errors create manual tasks via `TaskFactory` (`domain/manualtask`) and the external task-management service (`infrastructure/manualtask/TaskManagementClient`). `TasksSynchronize` (scheduled) reconciles task state.

Cross-cutting domains: `domain/group` (groups related errors into issues, optional Jira issue creation via `infrastructure/jira` + `IssueSummaryGenerator`/`IssueDescriptionGenerator`), `domain/housekeeping` (scheduled deletion of old errors), `domain/metrics` (scheduled Micrometer gauges), `domain/audit` (audit log of UI actions).

Web layer (`web/api`): REST controllers (`ErrorController`, `ErrorGroupController`, `DeadLetterReactivationController`) returning DTOs; search uses JPA Specifications (`*SearchSpecification`). `web/ui` serves the bundled Angular app and frontend config.

Scheduled jobs use **ShedLock** (JDBC lock provider) so only one instance runs each job in a clustered deployment: `ResendScheduler`, `HouseKeepingScheduler`, `TasksSynchronize`, `ErrorHandlingMetricsService`.

## Conventions & gotchas

- Entities use Lombok `@Builder`/`@Getter` with package-private constructors; mutations go through setters that bump a `modified` timestamp. Persistence types live in `infrastructure/persistence`, not in `domain`.
- Kafka events are Avro; test events are defined as Avro IDL under `src/test/avro` and generated by `jeap-messaging-avro-maven-plugin` at `generate-sources`.
- Configuration is `@ConfigurationProperties` classes named `*Properties` / `*ConfigProperties`, namespaced under `jeap.errorhandling.*`. Baseline defaults are in `src/main/resources/errorhandlerDefaultProperties.properties`.
- ShedLock version is pinned in the service pom (5.4.0) to override an older version from the parent — see the comment there before changing it.
- Consumer-driven contract tests use **Pact** (`au.com.dius.pact`); the provider `publish` goal runs in CI (Jenkins), gated by the `cdct-enable-publishing-local` profile for local runs.
- The parent is `ch.admin.bit.jeap:jeap-spring-boot-parent`.

## Versioning 

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- `setPomVersions.sh` updates the version across all module POMs.
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- Always keep the -SNAPSHOT postfix in the POMs, CI will remove it when releasing a version. Do not use the SNAPSHOT postfix in other places (CHANGELOG, publiccode.yml etc)
- Keep changelog entries concise and to the point, follow existing patterns
- Keep commit messages short, use the JIRA ID from the branch name as a prefix, do not use conventional commits (for example: "JEAP-1234 Added feature X")
- When bumping the version, also update the changelog, and update version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or patch version bump should be performed, and update the version accordingly.
