package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationRun.EndReason;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationRun.Result;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationTestSupport.uniqueName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests reactivation runs on dedicated source and target topics and consumer groups, so that the tests do not
 * depend on each other and do not interfere with the error event listeners of the EHS.
 */
class DeadLetterReactivationRunIT extends ErrorHandlingITBase {

    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private DeadLetterReactivationProperties properties;
    @Autowired
    private MeterRegistry meterRegistry;

    private DeadLetterReactivationTestSupport support;
    private String source;
    private String target;
    private String groupId;

    @BeforeEach
    void setUp() {
        support = new DeadLetterReactivationTestSupport(kafkaConfiguration, kafkaAdmin);
        source = uniqueName("dlt");
        target = uniqueName("target");
        groupId = uniqueName("reactivation-group");
        support.createTopic(source, 1);
        support.createTopic(target, 1);
    }

    @Test
    void properties_defaultsBound() {
        assertThat(properties.getMaxPollRecords()).isEqualTo(500);
        assertThat(properties.getIdleTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void reactivate_limitedByMaxRecords() throws Exception {
        support.produce(source, 10);

        Result first = reactivate(3);

        assertThat(first.forwarded()).isEqualTo(3);
        assertThat(first.endReason()).isEqualTo(EndReason.MAX_RECORDS);
        assertThat(support.countMessages(target)).isEqualTo(3);
        assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 3L);

        Result second = reactivate(100);

        assertThat(second.forwarded()).isEqualTo(7);
        assertThat(second.endReason()).isEqualTo(EndReason.BOUNDARY_REACHED);
        assertThat(support.countMessages(target)).isEqualTo(10);
        assertThat(new HashSet<>(support.readValues(target))).hasSize(10);
    }

    @Test
    void reactivate_limitedByTopicContent() {
        support.produce(source, 2);

        Result result = reactivate(100);

        assertThat(result.forwarded()).isEqualTo(2);
        assertThat(result.endReason()).isEqualTo(EndReason.BOUNDARY_REACHED);
        assertThat(support.countMessages(target)).isEqualTo(2);
    }

    @Test
    void reactivate_emptyTopic_endsWithoutJoiningTheGroup() throws Exception {
        Result result = reactivate(100);

        assertThat(result.forwarded()).isZero();
        assertThat(result.endReason()).isEqualTo(EndReason.BOUNDARY_REACHED);
        assertThat(support.committedOffsets(groupId, source)).isEmpty();
    }

    @Test
    void reactivate_messagesArrivingDuringRun_notForwardedByThatRun() {
        // Forwarding to the source topic itself publishes a message to the dead letter topic for every forwarded message
        support.produce(source, 3);

        Result first = run(source, source, 100, Map.of(), IDLE_TIMEOUT);

        assertThat(first.forwarded()).isEqualTo(3);
        assertThat(first.endReason()).isEqualTo(EndReason.BOUNDARY_REACHED);
        assertThat(support.countMessages(source)).isEqualTo(6);

        Result second = run(source, source, 100, Map.of(), IDLE_TIMEOUT);

        assertThat(second.forwarded()).isEqualTo(3);
        assertThat(second.endReason()).isEqualTo(EndReason.BOUNDARY_REACHED);
    }

    @Test
    void reactivate_slowStart_doesNotEndTheRun() {
        support.produce(source, 2);
        // The run cannot join the group until the stuck member has been removed after its max.poll.interval.ms
        try (KafkaConsumer<byte[], byte[]> ignored = support.createStuckGroupMember(groupId, source, Duration.ofSeconds(6))) {

            Result result = reactivate(100);

            assertThat(result.forwarded()).isEqualTo(2);
            assertThat(result.endReason()).isEqualTo(EndReason.BOUNDARY_REACHED);
            assertThat(result.duration()).isGreaterThan(Duration.ofSeconds(3));
        }
    }

    @Test
    void reactivate_noProgress_endsAfterIdleTimeoutAndKeepsProgress() throws Exception {
        support.produce(source, 3);
        assertThat(reactivate(1).forwarded()).isEqualTo(1);

        try (KafkaConsumer<byte[], byte[]> ignored = support.createStuckGroupMember(groupId, source, Duration.ofSeconds(10))) {

            Result result = run(source, target, 100, Map.of(), Duration.ofSeconds(3));

            assertThat(result.forwarded()).isZero();
            assertThat(result.endReason()).isEqualTo(EndReason.IDLE_TIMEOUT);
            assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 1L);
        }
    }

    @Test
    void reactivate_sendFails_batchNotCommittedAndRunStops() throws Exception {
        String smallTarget = uniqueName("small-target");
        support.createTopic(smallTarget, 1, Map.of(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "2000"));
        support.produce(source, 1);
        support.produce(source, 1, 0, 5000);
        support.produce(source, 1);
        double sendFailedRunsBefore = sendFailedRuns();

        Result result = run(source, smallTarget, 100, Map.of(), IDLE_TIMEOUT);

        assertThat(result.forwarded()).isZero();
        assertThat(result.endReason()).isEqualTo(EndReason.SEND_FAILED);
        assertThat(support.committedOffsets(groupId, source)).isEmpty();
        assertThat(sendFailedRuns()).isEqualTo(sendFailedRunsBefore + 1);
    }

    @Test
    void reactivate_concurrentRuns_eachMessageForwardedOnce() throws Exception {
        String concurrentSource = uniqueName("concurrent-dlt");
        support.createTopic(concurrentSource, 3);
        for (int partition = 0; partition < 3; partition++) {
            support.produce(concurrentSource, 40, partition, 0);
        }

        CompletableFuture<Result> first = CompletableFuture.supplyAsync(() -> run(concurrentSource, target, 120, Map.of(), IDLE_TIMEOUT));
        CompletableFuture<Result> second = CompletableFuture.supplyAsync(() -> run(concurrentSource, target, 120, Map.of(), IDLE_TIMEOUT));
        List<Result> results = List.of(first.get(), second.get());

        assertThat(results).allSatisfy(result -> assertThat(result.endReason())
                .isIn(EndReason.BOUNDARY_REACHED, EndReason.MAX_RECORDS, EndReason.CONCURRENT_RUN));
        assertThat(results.stream().mapToLong(Result::forwarded).sum()).isEqualTo(120);
        List<String> forwardedValues = support.readValues(target);
        assertThat(forwardedValues).hasSize(120);
        assertThat(new HashSet<>(forwardedValues)).hasSize(120);
        assertThat(support.committedOffsets(groupId, concurrentSource)).containsOnly(
                Map.entry(0, 40L), Map.entry(1, 40L), Map.entry(2, 40L));
    }

    @Test
    void reactivate_partitionsHeldByConcurrentRun_endsWithoutForwarding() throws Exception {
        support.produce(source, 5);

        try (AutoCloseable ignored = support.startActiveGroupMember(groupId, source)) {
            Result result = reactivate(100);

            assertThat(result.forwarded()).isZero();
            assertThat(result.endReason()).isEqualTo(EndReason.CONCURRENT_RUN);
        }
        assertThat(support.countMessages(target)).isZero();
    }

    private double sendFailedRuns() {
        Timer timer = meterRegistry.find(KafkaDeadLetterBatchConsumerProducer.REACTIVATION_RUNS_TIMER_METRIC)
                .tag(KafkaDeadLetterBatchConsumerProducer.OUTCOME_TAG, EndReason.SEND_FAILED.tagValue())
                .timer();
        return timer == null ? 0 : timer.count();
    }

    private Result reactivate(int maxRecords) {
        return run(source, target, maxRecords, Map.of(), IDLE_TIMEOUT);
    }

    private Result run(String from, String to, int maxRecords, Map<String, Object> overrides, Duration idleTimeout) {
        Map<String, Object> consumerOverrides = new HashMap<>(overrides);
        consumerOverrides.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return kafkaDeadLetterBatchConsumerProducer.reactivate(from, to, maxRecords, consumerOverrides, idleTimeout);
    }
}
