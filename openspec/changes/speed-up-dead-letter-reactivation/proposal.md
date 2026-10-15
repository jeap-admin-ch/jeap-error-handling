# Proposal

## Why

Reactivating messages from the dead letter topic (DLT) via `POST /api/deadletter/reactivate` is far too slow. A
measured run against embedded Kafka spends ~3 s per batch of at most 50 records, of which only ~15 ms is actual
forwarding. `KafkaDeadLetterBatchConsumerProducer` creates a new consumer and producer per batch and joins the
consumer group of the `MessageProcessingFailedEvent` listener (`spring.application.name`) every time: each batch waits
~2.5 s for the main listener to take part in a rebalance (revoking its error topic partitions), and ~0.5 s for closing
the clients, which triggers a second rebalance. 100 000 records → 2 000 batches → ≥ 100 minutes even locally, while
the main listener, which has to process the reactivated events, is stalled twice per batch.

The analysis also uncovered correctness problems in the current loop:

- The first poll of a batch may return nothing while the consumer is still being assigned the DLT partition (5 s
  poll timeout vs. a heartbeat-bound rebalance). The run then stops silently as if the DLT were empty — this also
  happens to a run that loses the partition to a concurrent run.
- `send()` without callback → `flush()` → `commitSync()` commits the offsets even if a send failed (e.g. a record
  exceeding the error topic's size limit), so such messages are skipped for good — not at-least-once.
- "Run until a poll returns nothing" can pick up reactivated events that fail again and land back on the DLT during
  the run.
- The controller's validation message says "between 1 and 500" while the accepted range is 1–100 000.

## What Changes

- Reactivation uses **one consumer and one producer per run** instead of one pair per 50-record batch, with a larger
  fetch size (500 records per poll). The consumer stays in the existing consumer group, so it joins and leaves the
  group once per run instead of once per batch.
- Each run is **bounded by the DLT end offsets captured at its start** (and by `maxRecords`); empty polls no longer end
  the run.
- Offsets are committed only after **all sends of a batch were acknowledged**; a failed send stops the run without
  committing that batch.
- A run that **makes no progress** for a configurable idle period ends with a warning.
- Concurrent runs keep relying on the group's partition assignment: they never read the same partition at the same
  time, so no lock is needed and requests are not rejected.
- Runs become **observable**: start/progress/end log entries (pending, forwarded, rate, end reason), a counter of
  forwarded messages (`eh_deadletter_reactivated_messages`) and a run timer tagged with the outcome
  (`eh_deadletter_reactivation_runs`). Today only a per-batch log line exists.
- The validation error message reflects the real range (1–100 000).
- No new Kafka permissions, no change to where progress is stored, no UI change.

## Capabilities

### New Capabilities

- `dead-letter-reactivation`: Manually triggered forwarding of messages from the EHS dead letter topic back to the
  error topic — request validation, run boundaries, progress recording, impact on the error event listener,
  behaviour of concurrent runs and observability.

### Modified Capabilities

(none — no existing specs in `openspec/specs/`)

## Impact

- **Code**: `infrastructure/kafka/KafkaDeadLetterBatchConsumerProducer` (rewritten consume/produce loop),
  `web/api/DeadLetterReactivationController` (message), new `*Properties` class under
  `jeap.errorhandling.deadLetterReactivation.*`, defaults in `errorhandlerDefaultProperties.properties`.
- **API**: `POST /api/deadletter/reactivate` unchanged (`202 Accepted`, `400` for invalid `maxRecords`).
- **Kafka / operations**: unchanged permissions and consumer group; the error event listener is rebalanced twice per
  run instead of twice per batch.
- **Metrics**: two new Micrometer metrics (`eh_deadletter_reactivated_messages`, `eh_deadletter_reactivation_runs`).
- **Tests**: `KafkaDeadLetterBatchConsumerProducerIT` (new scenarios), `DeadLetterReactivationControllerTest`.
- **Docs**: `docs/operations.md`, `docs/configuration.md`, `CHANGELOG.md`, version bump.
