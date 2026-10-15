# Tasks

## 1. Branch, version and configuration

- [x] 1.1 Create a feature branch named with the JIRA ID, ask the user whether a major, minor or patch bump applies, and bump with `setPomVersions.sh` to `x.y.z-SNAPSHOT`; verify all module POMs carry the new version
- [x] 1.2 Add `DeadLetterReactivationProperties` (`jeap.errorhandling.deadLetterReactivation.maxPollRecords`=500, `idleTimeout`=60s) with defaults in `errorhandlerDefaultProperties.properties`, and document both in `docs/configuration.md`; verify with a context test that the defaults bind and that the markdown is valid MDX

## 2. Consume/produce loop

- [x] 2.1 Rewrite `KafkaDeadLetterBatchConsumerProducer.consumeAndProduce` to use one `subscribe()` consumer (existing group, `max.poll.records` from properties) and one producer per run, polling with `poll(1s)` until the run ends (design D1); verify the existing `KafkaDeadLetterBatchConsumerProducerIT` scenarios still pass
- [x] 2.2 Implement the end-offset boundary, `maxRecords` limit, pausing (and re-pausing after rebalance) of finished partitions, and the progress rule using `committed()` for unassigned partitions (D2); add IT scenarios "limited by maxRecords" (10 on DLT, run 3 → 3 forwarded, 7 remain), "messages arriving during a run are not forwarded" and "slow start does not end the run" and verify they pass
- [x] 2.3 Implement send callbacks, `flush()`, failing the batch on any send exception, and `commitSync` with explicit "last forwarded offset + 1" offsets; end the run on `CommitFailedException`/`RebalanceInProgressException` (D4); add an IT where a dead letter record exceeds the error topic's `max.message.bytes` and assert its offset is not committed and the run stops, and verify "successive runs do not repeat messages" and header preservation still pass
- [x] 2.4 Implement the idle timeout (D5); add an IT (short `idleTimeout`) where the run cannot make progress and assert it ends with the warning and keeps committed progress
- [x] 2.5 Add an IT for the error event listener impact: register a rebalance listener counter on the error event listener container (or count `onPartitionsRevoked`) and assert at most two revocations during a run forwarding several batches
- [x] 2.6 Implement the run logs (start with pending count, progress every 10 s, end summary with end reason; per-batch lines to DEBUG) and the metrics `eh_deadletter_reactivated_messages` (incremented per committed batch) and `eh_deadletter_reactivation_runs` (timer with `outcome` tag, recorded in `finally`) (design D7); verify with ITs that the counter increases by the forwarded count and that a send failure records `outcome=send-failed`
- [x] 2.7 Document reactivation (endpoint/UI page, run boundary, at-least-once, concurrent runs, idle timeout, offset retention, log summary) in `docs/operations.md#dead-letter-topic` and add both metrics to the table in `docs/operations.md#metrics`; verify relative links and anchors resolve

## 3. Concurrent runs

- [x] 3.1 Implement ending a run whose first assignment contains no DLT partition with an INFO log (D3); add an IT that starts two runs at the same time over 120 messages and asserts each message is forwarded exactly once (distinct idempotence ids on the error topic) and both runs end

## 4. Throughput

- [x] 4.1 Add a throughput IT in its own class that puts 5000 messages on the DLT and asserts all are forwarded to the error topic (end offsets) within 30 s; verify it passes

## 5. API and release notes

- [x] 5.1 Fix the controller's validation message to "between 1 and 100000" and the misleading "completed" log line (D8); verify in `DeadLetterReactivationControllerTest` (202, 400 with message)
- [x] 5.2 Add a CHANGELOG entry (performance fix, at-least-once fix for failed sends, early-stop fix, new reactivation logs and metrics) and update version/date in `publiccode.yml`; verify versions match the POM without `-SNAPSHOT`

## 6. Integration verification

- [x] 6.1 Run `./mvnw -pl jeap-error-handling-service -am verify` and confirm all unit and integration tests pass
- [ ] 6.2 Compare run timings in the IT logs with the baseline (~3 s per 50-record batch) and record the result in the PR description

## Workflow follow-up

- Archive the change after review and merge.
