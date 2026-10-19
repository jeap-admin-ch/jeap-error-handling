# Operations

Operational aspects of a running Error Handling Service (EHS) instance: the dead letter topic, housekeeping,
metrics and multi-cluster behaviour.

## Dead letter topic

The EHS uses the regular jEAP messaging error handling for its own consumption. Since it cannot publish its
own failures to the error topic it consumes from, every system must order an additional topic, the dead
letter topic (DLT). Failed events the EHS cannot process are published there
(see [Message Flows](message-flows.md#error-handling-of-the-error-handling-service-itself)).

- Topic naming and permissions: see [Getting Started](getting-started.md#2-order-the-kafka-topics).
- The maximum message size of the DLT must match the size of the messages processed by the system.
- Configuration and startup validation: see [Kafka configuration](configuration.md#kafka).

The dead letter topic should always be empty — messages on it generally mean interrupted business
processes. The platform team sets up alerting on the DLT by default when the topic is ordered. If messages
end up on the DLT, they can be inspected with a Kafka tool (e.g. Kafdrop): like all failed messages they are
wrapped in a `MessageProcessingFailedEvent`, whose string attributes are directly readable.

### Reactivating dead letter messages

Once the cause of the failures is fixed, the messages on the DLT can be moved back to the error topic, where the
EHS processes them again. Users with the `error`/`retry` permission start a reactivation on the
*Reactivate dead letter messages* page of the UI, or with `POST /api/deadletter/reactivate?maxRecords=<n>`
(`maxRecords` between 1 and 100000; the endpoint responds with `202 Accepted`).

- **Run boundary:** a run forwards at most `maxRecords` messages, and only messages that were on the DLT when the
  run started. Messages arriving on the DLT during a run, e.g. reactivated messages that fail again, are left for
  the next run.
- **Progress and at-least-once delivery:** the messages are forwarded unchanged (key, value and headers). The
  progress is committed per batch in the consumer group of the EHS (`spring.application.name`), only after the
  error topic has acknowledged the forwarded messages. If publishing a message fails, the run stops without
  committing the batch, so the next run forwards it again. Messages forwarded more than once are recognized by
  the EHS as duplicates by their idempotence id.
- **Partition loss:** if a run loses ownership of partitions with uncommitted progress, it stops with
  `commit-failed` and discards its pending offsets rather than overwriting another consumer's progress.
  The uncommitted messages may be forwarded again by the next owner.
- **Offset retention:** while no reactivation runs, the consumer group is not subscribed to the DLT, so the broker
  removes the committed progress after its `offsets.retention.minutes` (7 days by default). The next run then
  starts at the earliest message on the DLT; errors that are still stored in the EHS are skipped as duplicates.
- **Concurrent runs:** runs started at the same time, on the same or another instance, share the DLT partitions
  through the consumer group, so no message is forwarded twice. A run that is assigned no DLT partition ends
  immediately and logs that a concurrent run continues the reactivation.
- **Impact on the error event listener:** a run joins and leaves the consumer group once, so the error event
  listener is rebalanced twice per run, independently of the number of forwarded messages.
- **Stalled runs:** a run that cannot make progress for the `idle-timeout` (default 60 seconds) ends with a
  warning, see [Configuration](configuration.md#dead-letter-reactivation).

Each run logs its start with the number of pending messages, its progress every 10 seconds, and a summary at
its end, for example:

```text
Dead letter reactivation ended (max-records): forwarded 5000 messages from topic ... to topic ... in 1811 ms (2760.9 messages/s)
```

The end reason is one of `max-records`, `boundary-reached` (all messages present at the start forwarded),
`idle-timeout`, `concurrent-run`, `send-failed`, `commit-failed` (consumer group rebalanced while committing) or
`error` (unexpected failure). The same values are used as `outcome` tag of the
`eh_deadletter_reactivation_runs` metric, see [Metrics](#metrics).

## Housekeeping

A nightly job deletes old errors so the database does not grow indefinitely. Deleted are errors that are
older than the configured maximum age **and** in one of the states `TEMPORARY_RETRIED`, `PERMANENT_RETRIED`,
`DELETED` or `PERMANENT`. Error groups without any remaining errors are deleted as well.

For retention, paging and scheduling properties, see
[Configuration](configuration.md#housekeeping-and-metrics).

## Metrics

The EHS publishes Micrometer metrics on the error rate and the number of open errors, intended for
monitoring and alerting per business application:

| Metric                                    | Type    | Description                                                                                                                                                 |
|-------------------------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `eh_created_temporary_errors`             | Counter | Errors classified as temporary since the start of the instance. Intended as basis for error rate alerting; sum over all instances in multi-instance setups. |
| `eh_created_permanent_errors`             | Counter | Errors classified as permanent since the start of the instance. Intended as basis for error rate alerting; sum over all instances in multi-instance setups. |
| `eh_temporary_retry_pending`              | Gauge   | Current number of temporary errors with a pending retry (total, from the database).                                                                         |
| `eh_permanent_open`                       | Gauge   | Current number of open permanent errors (manual task pending or open).                                                                                      |
| `eh_permanent_pending_manualtask_create`  | Gauge   | Permanent errors for which no manual task could be created yet (e.g. task service unreachable).                                                             |
| `eh_permanent_pending_manualtask_resolve` | Gauge   | Resolved permanent errors whose manual task could not be closed yet.                                                                                        |
| `eh_permanent_pending_manualtask_delete`  | Gauge   | Deleted permanent errors whose manual task could not be deleted yet.                                                                                        |
| `eh_open_errors_by_cluster`               | Gauge   | Current number of errors not in a final state, labelled by the effective Kafka or Modulith publication `cluster`. Both origins on the same cluster are aggregated. Once a cluster has been seen, it keeps being reported with the value 0. |
| `eh_error_groups_with_open_errors`        | Gauge   | Current number of error groups with open errors.                                                                                                            |
| `eh_deadletter_reactivated_messages`      | Counter | Messages forwarded from the dead letter topic to the error topic and acknowledged by the error topic since the start of the instance. Its rate is the reactivation throughput. |
| `eh_deadletter_reactivation_runs`         | Timer   | Duration of [dead letter reactivation](#reactivating-dead-letter-messages) runs, tagged with the reason the run ended (`outcome`).                            |

The gauge sampling interval is described in [Configuration](configuration.md#housekeeping-and-metrics).

## Scheduled jobs and clustering

All scheduled jobs use ShedLock with a JDBC lock provider, so they run on exactly one instance in a
clustered deployment:

| Job                     | Purpose                                                                                         |
|-------------------------|-------------------------------------------------------------------------------------------------|
| `ResendScheduler`       | Publishes due scheduled resends back to their original topics.                                  |
| `HouseKeepingScheduler` | Deletes old errors and empty error groups.                                                      |
| `TasksSynchronize`      | Reconciles the manual task state with Agir (completes pending create/close/delete transitions). |
| Metrics sampling        | Samples the gauge metrics from the database.                                                    |

## Multi-cluster support

The EHS supports multiple Kafka clusters as described in the jEAP messaging documentation, with one
particularity: the EHS **consumes exclusively from the default cluster**. When resending, the target cluster
is taken from the stored causing event, so signatures and schema references stay consistent with the
original cluster. Failed Modulith publications likewise store the consumed cluster and use that cluster's
transactional outbox for retry and discard commands. If a stored cluster has been removed from configuration, the
operator action fails without a default-cluster fallback and leaves the error open.

For command targeting and rolling-upgrade requirements, see
[Target-service headers for Modulith commands](configuration.md#target-service-headers-for-modulith-commands).

## Related

- [Message Flows](message-flows.md) — how failures of the EHS itself reach the dead letter topic
- [Configuration](configuration.md) — configuration properties and defaults
