# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [11.14.0] - 2025-09-19

### Changed

- Update parent from 27.1.1 to 27.2.0

## [11.13.0] - 2025-09-11

### Changed

- Update parent from 26.76.0 to 27.1.1

## [11.12.1] - 2025-09-04

### Bugfix

- RetryEvent is no longer possible when the state is 'deleted'.

## [11.12.0] - 2025-09-02

### Changed

- Update parent from 26.75.1 to 26.76.0

## [11.11.0] - 2025-08-29

### Changed

- Update parent from 26.74.0 to 26.74.1

### Fixed

- Fix error payload deserialization.

## [11.10.0] - 2025-08-26

### Changed

- Update parent from 26.72.0 to 26.74.0

## [11.9.0] - 2025-08-05

### Changed

- Update parent from 26.71.0 to 26.72.0

## [11.8.2] - 2025-08-04

### Bugfix

- Error is no longer deleted when cancelling or closing the "Ignore Error" modal.

## [11.8.1] - 2025-07-31

### Changed

- The sort for the error list view, is kept after filtering, reloading or by a resend.

## [11.8.0] - 2025-07-25

### Changed

- Update parent from 26.68.0 to 26.71.0

## [11.7.1] - 2025-07-10

### Changed

- The resend functionality from the dead letter topic can now handle headers, which is needed for keeping the signatures.

## [11.7.0] - 2025-07-08

### Changed

- Update parent from 26.67.0 to 26.68.0

## [11.6.0] - 2025-07-04

### Changed

- Update parent from 26.65.0 to 26.67.0

## [11.5.0] - 2025-06-26

### Changed

- Send only a list of IDs for mass mutation requests to minimize request size
- Update parent from 26.61.0 to 26.65.0

## [11.4.0] - 2025-06-17

### Changed

- Update parent from 26.57.0 to 26.61.0

## [11.3.0] - 2025-06-13

### Changed

- Update parent from 26.55.0 to 26.57.0

## [11.2.1] - 2025-06-11

### Changed

- Add public modifier for TaskDetailDto

## [11.2.0] - 2025-06-06

### Changed

- Update parent from 26.43.2 to 26.55.0

## [11.1.0] - 2025-04-15

### Changed

- Update parent from 26.43.1 to 26.43.2

## [11.0.0] - 2025-04-09

### Changed

- Breaking change: Validate frontend properties, they have to be set now
- Update parent from 26.42.0 to 26.43.1

## [10.3.0] - 2025-04-01

### Changed

- Update parent from 26.33.0 to 26.42.0

## [10.2.0] - 2025-03-06

### Changed

- Update parent from 26.31.0 to 26.33.0

## [10.1.0] - 2025-03-05

### Changed
- Update parent to 26.31.0 

## [10.0.1] - 2025-02-26

### Bugfix
- UI: Suppresses the useless and misleading error message from ServiceNavigation


## [10.0.0] - 2025-02-26

### Changed
- **BREAKING** - Removed default values for the following Spring properties:
  - `jeap.errorhandling.frontend.ticketingSystemUrl`
  - `log.deep-link.base-url`

## [9.10.0] - 2025-02-25

### Added

- Added @bbc/apache2-license-checker for automated license checking at build time.
- Use @license-checker instead of @generate-license-file as license file generator package.

## [9.9.0] - 2025-02-19

### Changed

- Update parent from 26.24.2 to 26.26.0
- Show whether a message/event has been signed or not in the ui

## [9.8.1] - 2025-02-18

### Changed

- DLT reactivation consumes messages until the desired number of records is reached

## [9.8.0] - 2025-02-13

### Changed

- Update parent from 26.23.0 to 26.24.2
- Disable license plugins for service instances

## [9.7.0] - 2025-02-10

### Changed

- Update parent from 26.22.3 to 26.23.0
- Publish to maven central

## [9.6.0] - 2025-02-07

### Added

- Added endpoint to reactivate messages from the dead letter topic to the error handling service. 

## [9.5.0] - 2025-01-27

### Changed

- Prepare repository for Open Source distribution

## [9.4.2] - 2025-01-23

### Bugfix

- Error Group List UI: Role #view and #edit have now an effect on the UI

## [9.4.1] - 2025-01-20

### Changed

- Error Group List UI: Truncate long error messages; Min. with for columns

## [9.4.0] - 2025-01-14

### Changed

- Added the module jeap-error-handling-service-instance which will instantiate a jeap error handling service instance when used as parent project.
- Update parent from 26.21.1 to 26.22.3

## [9.3.0] - 2024-12-19

### Changed

- Update parent from 26.15.0 to 26.21.1

## [9.2.0] - 2024-11-22

### Changed

- Update to @angular/core@18 @angular/cli@18
- Update @oblique/oblique to v12.0.1

## [9.1.0] - 2024-11-19

### Changed

- Replaced HeaderWidget.js with ServiceNavigation

- Improved several features in UI such as:
    - Setting 'PERMANENT' as default in search
    - Search for closing reason now works as a substring search
    - Stacktrace in detail view is now wrapped
    - Error message in detail view is not truncated anymore
    - Logs in detail view are now opened in a new tab

## [9.0.0] - 2024-11-12

### Changed

- Breaking change: the 'errorhandling' resource has been renamed to 'error' which means role names need to be updated.
  - errorhandling_#view -> error_#view
  - errorhandling_#retry -> error_#retry
  - errorhandling_#delete -> error_#delete
- Added a new view that collects similar permanent errors into groups. To access and/or modify groups the 'view' and/or 
  'edit' operations are needed on the 'errorgroup' resource:
  - errorgroup_#view
  - errorgroup_#edit

## [8.22.1] - 2024-10-29

### Changed

- Resending events from an unknown cluster automatically to the default cluster respecting a producer cluster override if configured.

## [8.22.0] - 2024-10-17

### Changed

- Update parent from 26.3.0 to 26.4.0

## [8.21.0] - 2024-09-25

### Changed

- Use URI variable in TaskManagementClient instead String concatenation

## [8.20.0] - 2024-09-20

### Changed

- Update parent from 26.2.2 to 26.3.0

## [8.19.1] - 2024-09-17

### Changed

- Add new error page in case of forbidden access
- Update jeap-spring-boot-parent from 26.0.0 to 26.2.2

## [8.19.0] - 2024-09-06

### Changed

- Update parent from 25.6.0 to 26.0.0

## [8.18.0] - 2024-09-05

### Changed

- Override with higher priority the value of the default property `jeap.messaging.kafka.errorTopicName` with
  `jeap.errorhandling.deadLetterTopicName` to avoid importing values of common configurations

## [8.17.0] - 2024-09-02

### Changed

- Update parent from 25.4.0 to 25.6.0
- Add a startup check to avoid a misconfigured dead letter topic from the default error topic

## [8.16.0] - 2024-08-22

### Changed

- Update parent from 25.3.1 to 25.4.0

## [8.15.2] - 2024-08-14

### Changed

- Update parent from 24.5.0 to 25.3.1
- Removed jeap-spring-boot-db-migration-starter dependency.

## [8.15.1] - 2024-07-18

### Changed

- Add flyway-database-postgresql dependency

## [8.14.0] - 2024-07-16

### Changed

- Update parent from 24.4.0 to 24.5.0, including the upgrade to Spring Boot 3.3.1

## [8.13.0] - 2024-07-10

### Changed

- add jeap-spring-boot-db-migration-starter dependency for rhos

## [8.12.0] - 2024-05-22

### Changed

- Upgrade to Angular version 17.3.9 and Oblique version 11.1.2
- Upgrade to typescript 5

## [8.11.0] - 2024-04-30

### Changed

- Added user interaction log on error details view.

## [8.10.0] - 2024-03-28

### Changed

- Update parent from 23.10.4 to 23.12.0

## [8.9.1] - 2024-03-12

### Changed

- Improve transaction handling in housekeeping service

## [8.9.0] - 2024-03-05

### Changed

- Upgraded to jeap parent 23.10.0
- Replaced WebClient with RestClient and removed reactive dependencies (webflux, ractor)

## [8.8.1] - 2024-02-05

### Changed

- Improve direct frontend route navigation redirection

## [8.8.0] - 2024-02-05

### Changed

- Update parent from 22.5.0 to 23.0.0

## [8.7.0] - 2024-01-25

### Changed

- Update parent from 22.2.3 to 22.5.0

## [8.6.0] - 2024-01-23

### Changed

- Update parent from 22.1.0 to 22.2.3

## [8.5.0] - 2024-01-16

### Changed

- Update parent from 22.0.0 to 22.1.0

## [8.4.1] - 2024-01-16

### Fixed

- Correctly deserialize event payloads from older versions without cluster name

## [8.4.0] - 2024-01-09

### Changed

- Update parent from 21.2.0 to 22.0.0

## [8.3.0] - 2023-12-19

### Changed

Log Deep Link simplified: the splunk specific configuration replaced with a generic configuration

- all splunk.* configurations are removed
- the new configuration log.deep-link.base-url can be used to configure the url of the logs

## [8.2.0] - 2023-12-14

### Changed

Upgrade jeap-parent from 21.0.2 to 21.2.0 (spring boot 3.2)

## [8.1.3] - 2023-12-07

### Fixed

Set correct frontend origin based on application URL configuration

## [8.1.2] - 2023-12-06

### Fixed

Set correct readOnly=true value to several methods executing only read operations

## [8.1.1] - 2023-11-30

### Changed

Retrieve generic data record deserializer for kafka cluster from jeap-messaging (adds AWS Glue Schema Registry support)

## [8.1.0] - 2023-11-24

### Changed

Add multi-kafka-cluster support for the error handling service

## [8.0.1] - 2023-11-29

### Fixed

Fix silent renew by providing correct redirect uri to OAuth

## [8.0.0] - 2023-11-21

### Changed

Upgrade to jeap-parent 21.0.0 with multi-cluster support in jeap-messaging

## [7.0.2] - 2023-10-27

### Fixed

Fix in DeleteDialog: Cancel not possible when deleting

## [7.0.1] - 2023-08-23

### Fixed

Fix date selection being reset in UI after search with DE / CH browser locales

## [7.0.0] - 2023-08-16

### Changed

Upgraded to Spring Boot 3.1.

## [6.22.0] - 2023-08-09

### Changed

- Update parent from 19.16.1 to 19.17.0

## [6.21.0] - 2023-08-08

### Changed

- Update parent from 19.14.0 to 19.16.1

## [6.20.0] - 28.06.2023

- retrieve and store traceIdHigh from traceContext to support traceId128

## [6.19.0] - 2023-06-22

### Changed

- updated to jeap-spring-boot-parent 19.13.0 (refactoring of avro serialization and deserialization in jeap-messaging)

## [6.18.0] - 2023-06-21

### Changed

- use query params to store the filter values of the search error-list

## [6.17.0] - 2023-06-20

### Changed

- remove temporary-error-list-page
- refactor state filter
- fix traceId and eventId search
- add tooltip for reload and delete buttons
- open splunk link in new window

## [6.16.0] - 2023-06-13

### Changed

- Updated jeap-spring-boot-parent from 19.12.1 to 19.12.2

## [6.15.2] - 2023-06-07

### Changed

- Upgraded shedlock libraries from 4.4.0 to 5.4.0 to improve exception handling during maintenance upgrades (JEAP-3580
  and DAZITTPE-9704)

## [6.15.1] - 2023-06-02

### Changed

- Show default error-state in dropdown

## [6.15.0] - 2023-05-30

### Changed

- Update parent from 19.12.0 to 19.12.1

## [6.14.0] - 2023-05-30

### Changed

- Support reading message metadata from failed event if available to avoid deserializing the original event

## [6.13.1] - 2023-05-16

### Fixed

- Avoid displaying retried temporary errors by default

## [6.13.0] - 2023-05-02

### Added

- Enhance UI to encompass simultaneous deleting and resending of multiple events
- Show dialog when manually closing event for entering an optional reason
- Allowed previously closed errors to be manually resent
- Add field to search for stacktrace using regex
- Add field to search closed error given the closing reason
- Implement caching strategy for eventSources, errorCodes and eventNames

### Changed

- Updated qd-auth from 1.0.19 to 13.3.0

## [6.12.0] - 2023-04-21

### Changed

- Update parent from 19.2.0 to 19.10.1

## [6.11.0] - 2023-02-23

### Changed

- Removed search page component and integrated it into the error-list
- Update Angular from 10.2.4 to 13.3.12
- Update Oblique from 5 to 8
-

### Added

- Autocomplete dropdown when searching for event names
- Clear button for each form field
- Endpoint for retrieving configured splunk query template
- Tests for template configuration
- Deeplink to Splunk for each event in error-list and -detail
- Internationalisation keys for all languages

## [6.10.0] - 2023-02-21

### Changed

- Update parent from 19.0.1 to 19.2.0

## [6.9.0] - 2023-02-09

### Changed

- Updated parent from 18.4.0 to 19.0.1
- For performance reasons, remove Spring Data queries in favor of native queries for batch message deletion

## [6.8.0] - 2022-11-17

### Changed

- Updated parent from 18.2.0 to 18.4.0
- Fixed error-handling-service consumer contract validation

## [6.7.0] - 2022-10-31

### Changed

- Update parent from 18.0.0 to 18.2.0

## [6.6.1] - 2022-10-13

### Fixed

- Fixed java flyway migration: String type for UUID columns was not compatible with some postgresql versions

## [6.6.0] - 2022-10-06

### Changed

- Update parent from 17.3.0 to 18.0.0

## [6.5.1] - 2022-10-04

### Fixed

- Replace null chars from the stacktrace of the original error with blank to avoid errors when saving the original error
  to the database

## [6.5.0] - 2022-09-21

### Changed

- Update parent from 17.2.2 to 17.3.0

## [6.4.0] - 2022-09-13

### Changed

- Update parent from 17.1.0 to 17.2.2
- Remove component scan of jeap messaging and import kafka configurations separately in integration tests

## [6.3.0] - 08.08.2022

### Changed

- Update to jEAP spring boot parent 17.1.0

## [6.2.1] - 11.05.2022

### Fixed

- Extracting metadata from the causing event now also works for avro schemas that don't contain the java type hint
  "avro.java.string":"String", i.e. metadata extraction should now also work for schemas that have been registered by  
  non-java microservices.

## [6.2.0] - 02.05.2022

### Added

- add metric tag 'causing_service' to existing metric 'eh_created_permanent_errors'

## [6.1.0] - 05.04.2022

### Added

- New Search Service to search errors with criteria
- New UI-Page to search errors with filter

## [6.0.1] - 21.03.2022

### Changed

- The housekeeping scheduler additionally deletes old causing events and scheduled resends

## [6.0.0] - 03.03.2022

### Changed

- The error handling service no longer sets the 'javax.net.ssl.trustStore' property to the 'truststore.jks' resource on
  start-up.
  Therefore you now will have to define the truststore for SSL connections yourself in your error handling service
  instance. For
  microservices in Cloudfoundry you can do this e.g. in the Cloudfoundry manifest file.

## [5.7.1] - 17.01.2022

### Changed

- Fixed the javadoc generation

## [5.7.0] - 07.01.2022

### Changed

- Use the new TracingKafkaProducerFactory to create wrapped producer with kafka tracing
- update to jeap-spring-boot-parent 15.3.0

## [5.6.0] - 28.12.2021

### Added

- New Scheduler which deletes old Errors (in State 'TEMPORARY_RETRIED' or 'PERMANENT_RETRIED').
  How old they have to be, can be configured (Default is 12 Months).

## [5.5.3] - 27.12.2021

### Fixed

- UI: Fix Pagination
- UI: Fix Silent Refresh
- UI: Display Version Number form POM

## [5.5.2] - 23.12.2021

### Change

- update to jeap-spring-boot-parent 15.2.0 (spring boot 2.6.2)

## [5.5.1] - 22.12.2021

### Fixed

- avoid endless task management calls when attempting to close a deleted task

## [5.5.0] - 15.12.2021

### Added

- the trace context from the original error message is stored in the database and re-injected when resending the message

### Changed

- Upgraded to jeap-spring-boot-parent 15.1.3

## [5.4.2] - 21.09.2021

### Changed

- Improved error message when handling non-avro message payloads

## [5.4.1] - 24.08.2021

### Changed

- Reverted integration tests back to H2 (removed testcontainers)

## [5.4.0] - 23.08.2021

### Changed

- Using native UUID type for persisted UUIDs

## [5.3.0] - 17.08.2021

### Added

- Prometheus metrics for error rates and counts

## [5.2.0] - 09.07.2021

### Changed

- Upgraded to jeap-spring-boot-parent 14.2.0 (optimized kafka default configuration)

## [5.1.1] - 23.06.2021

### Changed

- Upgraded to jeap-spring-boot-parent 14.0.4 (spring boot 2.5.1)

## [5.1.0] - 10.06.2021

### Added

- Added configuration properties for Task Types

## [5.0.2] - 04.06.2021

### Changed

- Update jEAP Parent to 14.0.3 (fixes issue with keycloak token parsing)

## [5.0.1] - 03.06.2021

### Changed

- Update jEAP Parent to 14.0.1

## [5.0.0] - 01.06.2021

### Changed

- Update jEAP Parent to 14.0.0 (Spring Boot 2.5.0)

## [4.3.3] - 06.05.2021

### Changed

- Use @EmbeddedKafka from spring for tests

## [4.3.2] - 06.05.2021

### Changed

- Update jeap parent to 13.7.1 (exposees readiness actuator endpoint)

## [4.3.1] - 19.04.2021

### Fixed

- Schedule retry or mark error as permanent if a technical error occurs during a retry, avoids blocking all other
  retries

## [4.3.0] - 08.04.2021

### Changed

- Changed task type name used to report permanent failures to Agir from 'EventProcessingFailedTaskType' to '
  errorhandling' as requested by Agir.
- Adapted the pact with Agir to the new fine-grained semantic application role support by Agir.

## [4.2.1] - 08.03.2021

### Changed

- Updated QDAuth-Service to v1.0.19

## [4.2.0] - 01.03.2021

### Changed

- Use error-handling to publish error events in a dead-letter-topic configured with
  `jeap.errorhandling.deadLetterTopicName`. This dead-letter-topic must be different from the topic configured with
  `jeap.errorhandling.topic`.
- Updated jeap-spring-boot-parent to 13.6.7

## [4.1.1] - 23.02.2021

### Changed

- Updated jeap-spring-boot-parent to 13.6.3

## [4.1.0] - 22.02.2021

### Changed

- Updated jeap-spring-boot-parent to 13.6.2 (REST tracing improvements, jeap-messaging contract logging improvements)

## [4.0.3] - 11.02.2021

### Bugfix

- Fix message ID for command retrieved from identity.eventId instead of identity.id

## [4.0.2] - 04.02.2021

### Bugfix

- Fix URL to /api/error

## [4.0.1] - 29.01.2021

### Changed

- Use QDAuth-Service v1.0.12 for jeap-errorhandling-ui

## [4.0.0] - 26.01.2021

### Changed

- Switched to jeap-messaging (via new jeap-spring-boot-parent)
- Added support for the 'old' EventProcessingFailedEvents

## [3.0.0] - 08.01.2021

### Changed

- Switched to semantic roles
- Upgraded UI to Angular 10 and Oblique 5

## [2.0.1] - 2020-11-25

### Changed

- Updated to latest parent, including Javadoc

## [2.0.0] - 2020-11-16

### Changed

- Switched to agir_#update role instead of agir_write role in Pact specification.

## [1.8.2] - 2020-11-13

### Changed

- Updated jeap parent to 10.1.0 which contains a fix to no longer request unnecessary offline tokens from Keycloak.

## [1.8.1] - 2020-09-03

### Changed

- Updated jeap parent to 9.0.0-76
- Updated to jeap-spring-boot-parent version 8.0.0 and adapted Pact tests to reflect the Pact related changes made in
  the new jeap parent version.

### Fixed

- AvroDeserializer instances now get closed, improved synchronization on lazy initialization of task types.

## [1.8.0] - 2020-08-06

### Changed

- Updated dependencies and Spring Boot 2.3

### Fixed

- Fixed a bug in configuring the mobile PAMS header widget that caused the PAMS header widget to always fetch its login
  state
  from the REF PAMS environment ignoring the pamsEnvironment configuration property which would lead to an immediate
  logout of the
  user if the user would not happen to be logged-in on REF already at the same time.

## [1.7.1] - 2020-07-01

### Changed

- Make sure system in task submitted to agir matches task type configuration

## [1.7.0] - 2020-06-25

### Changed

- Updated to domain event version 5.1.0 with processId

## [1.6.6] - 2020-06-22

### Changed

- Fixed wrong folder name for postgresql after migrate scipts

## [1.6.5] - 2020-06-22

### Changed

- Update to latest jeap parent 6.0.2 incl, fix for startup bug

## [1.6.4] - 2020-06-16

### Changed

- Update to latest jeap parent 6.0.1

## [1.6.3] - 2020-06-15

### Changed

- Removed logging of HTTP headers on requests to task management

## [1.6.2] - 2020-06-12

### Fixed

- Fix error states not updated after task management synchronisation
- Upgraded to jeap-spring-boot-parent version 5.3.2-50

## [1.6.1] - 2020-06-05

### Changed

- Removed unused config property 'role' on DefaultTaskFactoryProperties
- Upgraded to jeap-spring-boot-parent version 5.3.1-48

## [1.6.0] - 2020-05-29

### Changed

- Adapted to the changed authentication method expectations of the Agir task management service, i.e. switched from
  basic auth to OAuth2.
- Upgraded to jeap-spring-boot-parent version 5.0.1-42
- Switched to jeap-spring-boot-swagger-starter for OpenAPI/Swagger integration.

## [1.5.0] - 2020-04-28

### Changed

- Adapted the manual task service integration to the current Agir manual task service API.
- Added consumer driven contract testing with Pact for the Agir manual task service.

## [1.4.0] - 2020-04-15

### Changed

* Use new EventProcessingFailedEvent instead of errorevent
* Update to domainevent library 4.6.0

## [1.4.0] - 2020-04-15

### Changed

- Upgraded to jeap-spring-boot-parent version 4.7.0-28 to add support for accessing basic auth protected Kafka schema
  registries.

## [1.3.1] - 2020-03-23

### Fixed

- Application was packaged as Fat-Jar preventing it from use as dependency

## [1.3.0] - 2020-03-23

### Added

- Connection to manual task service

#

## [1.2.1] - 2020-03-12

### Fixed

- Fixed 'Resending messages fails on a Kafka cluster that requires authentication'.

## [1.2.0] - 2020-03-06

### Added

- Add frontend
- Various configuration options

### Changed

- Data model: 1 Event -> n Errors, one for each retry

### Fixed

- Rescheduling timing

## [1.1.0] - 2020-02-27

### Added

- Added persistence for the failed events received
- Added automatic resending of events that failed with a temporary error

## [1.0.0] - 2020-02-05

### Added

- Basic error handling skeleton including a queue listener, monitoring/loggin/auth integration,
  Flyway / JPA using PostgreSQL, local development setup

### Changed

Nothing

### Removed

Nothing
