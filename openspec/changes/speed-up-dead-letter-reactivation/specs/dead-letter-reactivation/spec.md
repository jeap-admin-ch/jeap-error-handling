# Spec Delta

## Purpose

Lets operators move messages from the Error Handling Service's own dead letter topic back to the error topic, so
that failures of the EHS itself can be processed again once their cause is fixed.

## ADDED Requirements

### Requirement: Reactivation request validation
The system SHALL accept a reactivation request with a `maxRecords` value between 1 and 100000 (inclusive) from users
with the `error`/`retry` permission, start the run in the background and respond with `202 Accepted`. Requests with a
value outside that range SHALL be rejected with `400 Bad Request` and a message stating the valid range 1–100000.

#### Scenario: Valid request starts a run
- **WHEN** an authorized user requests reactivation with `maxRecords=1000`
- **THEN** the system responds with `202 Accepted` and forwards dead letter messages in the background

#### Scenario: Out-of-range request rejected
- **WHEN** an authorized user requests reactivation with `maxRecords=0` or `maxRecords=100001`
- **THEN** the system responds with `400 Bad Request` and a message stating that `maxRecords` must be between 1 and 100000

### Requirement: Faithful forwarding
Each reactivated message SHALL be published to the configured error topic with its original key (or no key if the
original had none), its original value bytes and all of its original headers.

#### Scenario: Key, value and headers preserved
- **WHEN** a dead letter message with a key, a value and signature/encryption headers is reactivated
- **THEN** the message on the error topic has the same key, the same value bytes and contains all original headers

### Requirement: Run boundary
A run SHALL forward at most `maxRecords` messages and SHALL only forward messages that were on the dead letter topic
when the run started. A run SHALL end when `maxRecords` is reached or every partition has been read up to its end
position captured at the start; a temporarily empty fetch SHALL NOT end the run.

#### Scenario: Limited by maxRecords
- **WHEN** 10 messages are on the dead letter topic and a run with `maxRecords=3` is started
- **THEN** exactly 3 messages are forwarded and the remaining 7 stay pending for a later run

#### Scenario: Limited by topic content
- **WHEN** 2 messages are on the dead letter topic and a run with `maxRecords=100` is started
- **THEN** exactly 2 messages are forwarded and the run ends

#### Scenario: Messages arriving during a run are not forwarded by that run
- **WHEN** a message is published to the dead letter topic after a run has started (e.g. a reactivated message that fails again)
- **THEN** that run does not forward it, and it is forwarded by a subsequent run

#### Scenario: Slow start does not end the run
- **WHEN** a run is started and the first fetch returns no messages because the run is still being assigned the dead letter partitions
- **THEN** the run continues and forwards the pending messages

### Requirement: Progress is persisted
The system SHALL record reactivation progress per dead letter topic partition in the EHS's existing Kafka consumer
group, only after the error topic has acknowledged the forwarded messages. A run SHALL resume at the recorded
position; where none is recorded, it SHALL start at the earliest available message.

#### Scenario: Successive runs do not repeat messages
- **WHEN** two messages are on the dead letter topic and two runs with `maxRecords=1` are executed one after the other
- **THEN** each message is forwarded exactly once

#### Scenario: Failed publication is not recorded
- **WHEN** publishing a message to the error topic fails during a run, including non-retriable failures such as a record exceeding the topic's size limit
- **THEN** the run stops, the failure is logged, the position of that message is not recorded, and the next run forwards it again

#### Scenario: Positions of the previous version are kept
- **WHEN** the EHS is upgraded to this version after messages were reactivated by the previous version
- **THEN** the first run continues after the messages already reactivated by the previous version

#### Scenario: No recorded position
- **WHEN** a dead letter topic partition has no recorded position, because it was never reactivated or the position expired according to the broker's offset retention
- **THEN** forwarding for that partition starts at the earliest available message

### Requirement: No additional Kafka permissions
Reactivation SHALL NOT require any Kafka topic or consumer group permission beyond those the EHS already needs.

#### Scenario: Upgrade without ACL changes
- **WHEN** an EHS instance is upgraded to this version without any change to its Kafka ACLs
- **THEN** dead letter reactivation works

### Requirement: Limited impact on the error event listener
A reactivation run SHALL cause the EHS's error event listeners to lose their partitions at most twice (when the run
starts and when it ends), independent of the number of forwarded messages.

#### Scenario: Large run causes at most two revocations
- **WHEN** a run forwards 5000 messages while the error event listener is consuming the error topic
- **THEN** the error event listener's partitions are revoked at most twice during the run

### Requirement: Concurrent runs
Reactivation requests SHALL NOT be rejected because another run is in progress, on the same or another instance.
Concurrent runs SHALL NOT forward the same dead letter message more than once, except for messages forwarded again
after a failed run as described for progress recording.

#### Scenario: Two concurrent runs
- **WHEN** 120 messages are on the dead letter topic and two runs with `maxRecords=120` are started at the same time
- **THEN** each of the 120 messages is forwarded exactly once

#### Scenario: Run without assigned partitions
- **WHEN** a run is not assigned any dead letter partition because a concurrent run holds them
- **THEN** the run ends without error and logs that a concurrent run continues the reactivation

### Requirement: Stalled runs end
If no message can be read for a configurable idle period (default 60 seconds) although the run boundary has not been
reached, the run SHALL end and log a warning that includes the number of forwarded messages.

#### Scenario: Dead letter partition unavailable
- **WHEN** a run cannot fetch from a dead letter partition for longer than the idle period
- **THEN** the run ends, keeps the recorded progress of all acknowledged messages and logs a warning with the number of forwarded messages

### Requirement: Reactivation is observable
Each run SHALL log its start with the number of pending messages, its progress at a regular interval and its end
with forwarded count, duration, rate and end reason. The system SHALL publish a counter of forwarded messages and a
run duration metric tagged with the end reason.

#### Scenario: Run summary logged
- **WHEN** a run ends
- **THEN** a log entry states the number of forwarded messages, the duration, the rate in messages per second and the end reason (`max-records`, `boundary-reached`, `idle-timeout`, `concurrent-run`, `send-failed`, `commit-failed` or `error`)

#### Scenario: Progress logged during a long run
- **WHEN** a run lasts longer than the progress interval (10 seconds)
- **THEN** a log entry with the forwarded and still pending messages and the current rate is written at each interval

#### Scenario: Forwarded messages counted
- **WHEN** a run forwards 3 messages that are acknowledged by the error topic
- **THEN** the counter `eh_deadletter_reactivated_messages` increases by 3

#### Scenario: Run duration and outcome recorded
- **WHEN** a run ends because the publication of a message failed
- **THEN** the timer `eh_deadletter_reactivation_runs` records the run duration with the tag `outcome=send-failed`

### Requirement: Throughput
The fixed overhead of a run SHALL NOT depend on `maxRecords`; the run time SHALL be dominated by the number of
forwarded messages.

#### Scenario: Large run completes quickly
- **WHEN** 5000 messages are on the dead letter topic and a run with `maxRecords=5000` is started
- **THEN** all 5000 messages are forwarded within 30 seconds in the integration test environment
