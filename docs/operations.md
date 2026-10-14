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
