# Design

## Context

See proposal.md (Why) for the measured cost breakdown. Relevant current state:

- `DeadLetterReactivationController` validates `maxRecords` and calls the `@Async`
  `KafkaDeadLetterBatchConsumerProducer.consumeAndProduce(maxRecords)`; the HTTP response is `202` before any work
  happens.
- The consumer is built from `KafkaConfiguration.consumerConfig(defaultCluster)`: `group.id = spring.application.name`
  (the group of the error event listener containers), jEAP consumer interceptors, `byte[]` deserializers,
  `auto.offset.reset=earliest`, auto-commit disabled. It `subscribe()`s to the DLT, polls once (5 s), forwards,
  `flush()`es, `commitSync()`s and closes — per batch of at most 50 records.
- Progress is therefore stored as the committed DLT offsets of that group. Kafka assigns each DLT partition to one
  member of the group, which also keeps concurrent runs apart (verified with two concurrent runs over 120 messages:
  every message forwarded once; the run that did not get the partition stopped early).
- The producer is built from `producerConfig(defaultCluster)` with byte-array serializers and without interceptors.
- Kafka clients 4.2, classic group protocol, eager `RangeAssignor`: every join or leave revokes all partitions of all
  members, including the error event listener.
- Reactivation only uses the default cluster; this stays so.

## Goals / Non-Goals

**Goals:**
- One group join and one leave per run, independent of `maxRecords`.
- Exact run boundaries, at-least-once forwarding, no change to where progress is stored.
- No new Kafka permissions, no API or UI change apart from the corrected validation message.

**Non-Goals:**
- Avoiding group membership altogether (see D1, alternative).
- Mutual exclusion of runs or a `409` response (the group's partition assignment already prevents duplicates).
- Parallel workers. One consumer fetches from all DLT partitions it is assigned.
- Changing the applied jEAP consumer interceptors (per-record INFO log line, metrics) — unchanged behaviour.
- Reporting run progress/results to the UI (the run stays fire-and-forget; progress is visible in logs and metrics).
- Multi-cluster reactivation.

## Decisions

### D1: One `subscribe()` consumer per run in the existing group
`consumeAndProduce` creates one consumer and one producer, `subscribe()`s once and keeps polling until the run ends.
This removes the per-batch join (~2.5 s), the per-batch close (~0.5 s) and the two rebalances per batch; one join and
one leave per run remain.

*Alternative considered:* `assign()` without group membership (zero rebalances). Rejected: committing offsets to the
existing group is impossible without being a member (the broker rejects commits from non-members while the group has
members), so it requires a new consumer group with its own ACL (breaking for operations), seeding of offsets from the
existing group, and a distributed lock to replace the exclusion the group's partition assignment provides today. It
would save only ~3 s and two rebalances per run.

### D2: Run boundary from end offsets captured at start
At run start, `partitionsFor(deadLetterTopic)` and `endOffsets(partitions)` are captured. Per partition, the
*progress* is the consumer `position()` if the partition is assigned to this run, otherwise the group's committed
offset (`committed()`, queried only when all assigned partitions are done). The run ends when:
- `forwarded == maxRecords`, or
- every partition's progress has reached its captured end offset.

Records at or beyond a partition's captured end are neither forwarded nor counted; such a partition is `pause()`d
(re-paused in `onPartitionsAssigned` after a rebalance). Empty polls just loop (`poll(1s)`), so a slow join or fetch
cannot end the run early. Messages that land on the DLT during the run are left for the next run.

### D3: Waiting for the assignment, concurrent runs
A `ConsumerRebalanceListener` records the current assignment. Until the first assignment arrives, the run keeps
polling (bounded by the idle timeout, D5). If the first assignment contains no DLT partition — a concurrent run holds
all of them — the run ends with an INFO log that a concurrent run continues the reactivation. If partitions move to a
concurrent run later, the progress rule of D2 (committed offsets for unassigned partitions) lets this run end once the
other run has reached the boundary, or via the idle timeout.

### D4: Send acknowledgement before commit
```
loop:
  records = poll(1s)
  for record in records (within boundary and maxRecords):
      producer.send(copy of key/value/headers, callback) ; remember last forwarded offset per partition
  producer.flush()
  if any send callback reported an exception -> throw (batch not committed)
  commitSync(Map: last forwarded offset + 1 per partition)
```
Committing explicit offsets (not the consumer position) is required because a poll can return records beyond
`maxRecords` or beyond the captured end that were not forwarded. Because each batch is committed before the next
`poll()`, revoked partitions resume at the committed offset. If `commitSync` throws `RebalanceInProgressException`,
the offsets are kept and the commit is retried in `onPartitionsRevoked` or after the next `poll()`; the run does not
end before they are committed. A `CommitFailedException` ends the run with a warning (`commit-failed`); the
uncommitted batch is forwarded again by the next run (at-least-once). Unexpected exceptions end the run with the
reason `error`. Duplicate error events are skipped by the EHS's existing
idempotence check (`ErrorEventHandlerService` → `ErrorService.isEventDuplicate`).

### D5: Idle timeout
The run tracks the time of the last progress (assignment received, records forwarded, or position advanced). If no
progress was made for `idleTimeout`, the run ends with a WARN log including the number of forwarded messages; the
recorded progress of acknowledged batches is kept.

### D6: Configuration
New `DeadLetterReactivationProperties` (`@ConfigurationProperties("jeap.errorhandling.deadLetterReactivation")`)
with `maxPollRecords` (default `500`) and `idleTimeout` (default `60s`); defaults in
`errorhandlerDefaultProperties.properties`.

### D7: Observability
The run computes the pending messages at start (sum over partitions of captured end offset minus start position;
for partitions assigned later, added on assignment). It logs at INFO: start (pending, `maxRecords`), progress every
10 s (forwarded, pending, rate) and end (forwarded, duration, rate, end reason). The per-batch INFO lines are reduced
to DEBUG.

Metrics are registered on the existing `MeterRegistry`, following the `eh_*` naming of `ErrorHandlingMetricsService`:
- `eh_deadletter_reactivated_messages` (Counter): incremented by the number of records committed per batch, so it
  only counts messages acknowledged by the error topic. Throughput is its rate.
- `eh_deadletter_reactivation_runs` (Timer, tag `outcome` = end reason): recorded once per run in a `finally` block,
  so failed runs are recorded too.

*Alternatives considered:* a live pending gauge (rejected for now: per-instance gauge semantics are confusing with
concurrent runs on several instances; Kafka lag monitoring of the listener group on the DLT already shows it) and a
UI progress view (requires run state shared across instances and a new endpoint).

### D8: Controller
Only the validation message changes ("between 1 and 100000"). The log line "Dead-letter reactivation process
completed" is changed to "started", as the run is asynchronous.

## Risks / Trade-offs

- [The error event listener is still rebalanced twice per run; a busy listener delays the join] → bounded by the
  listener's own poll loop and covered by the idle timeout; acceptable compared to twice per batch.
- [Offsets of the DLT in the listener group expire after the broker's `offsets.retention.minutes` (default 7 days)
  without a run, because the group is not subscribed to the DLT in between] → unchanged from today; the next run then
  starts at the earliest message and the EHS idempotence check skips errors that were already recorded.
- [Larger batches (500) mean more messages forwarded again after a failed commit] → at-least-once by design; duplicates
  are skipped by the idempotence check.
- [A run holding the DLT partitions for a long time delays a concurrent run] → the concurrent run either ends
  immediately (no assignment) or continues where the first stopped; no duplicates.
- [Per-record INFO logging of the jEAP consumer interceptor for 100 000 records] → unchanged behaviour, accepted.

## Migration Plan

No migration: same consumer group, same permissions, same API. Rollback to the previous version is possible at any
time; it continues from the committed offsets.
