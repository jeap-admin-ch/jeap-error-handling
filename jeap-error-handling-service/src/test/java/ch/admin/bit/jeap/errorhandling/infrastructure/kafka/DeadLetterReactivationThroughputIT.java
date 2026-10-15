package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationRun.EndReason;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationRun.Result;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationTestSupport.uniqueName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs a large reactivation in the consumer group of the EHS error event listeners. The run uses dedicated topics,
 * so that the forwarded messages are not processed by the EHS.
 */
class DeadLetterReactivationThroughputIT extends ErrorHandlingITBase {

    private static final int MESSAGE_COUNT = 5000;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void reactivate_largeRun_fastAndAtMostTwoRebalancesOfTheErrorEventListener() {
        DeadLetterReactivationTestSupport support = new DeadLetterReactivationTestSupport(kafkaConfiguration, kafkaAdmin);
        String source = uniqueName("throughput-dlt");
        String target = uniqueName("throughput-target");
        support.createTopic(source, 1);
        support.createTopic(target, 1);
        support.produce(source, MESSAGE_COUNT);
        Map<String, Double> rebalancesBefore = errorEventListenerRebalances();
        assertThat(rebalancesBefore).isNotEmpty();

        Result result = kafkaDeadLetterBatchConsumerProducer.reactivate(source, target, MESSAGE_COUNT, Map.of(), Duration.ofSeconds(60));

        assertThat(result.endReason()).isEqualTo(EndReason.MAX_RECORDS);
        assertThat(result.forwarded()).isEqualTo(MESSAGE_COUNT);
        assertThat(result.duration()).isLessThan(Duration.ofSeconds(30));
        assertThat(support.countMessages(target)).isEqualTo(MESSAGE_COUNT);

        // The error event listener is rebalanced when the run joins and when it leaves the consumer group
        await("error event listener rebalanced after the run").atMost(FORTY_SECONDS)
                .until(() -> rebalancesBefore.keySet().stream()
                        .allMatch(client -> errorEventListenerRebalances().get(client) - rebalancesBefore.get(client) >= 2));
        await("no further rebalances").during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10))
                .until(() -> rebalancesBefore.keySet().stream()
                        .allMatch(client -> errorEventListenerRebalances().get(client) - rebalancesBefore.get(client) <= 2));
    }

    /**
     * @return the number of completed rebalances by consumer client of the error event listener containers
     */
    private Map<String, Double> errorEventListenerRebalances() {
        Map<String, Double> rebalances = new HashMap<>();
        applicationContext.getBeansOfType(ConcurrentMessageListenerContainer.class).values().forEach(container -> {
            Map<String, Map<MetricName, ? extends Metric>> metrics = container.metrics();
            metrics.forEach((client, clientMetrics) -> clientMetrics.forEach((name, metric) -> {
                if (name.name().equals("rebalance-total") && name.group().equals("consumer-coordinator-metrics")) {
                    rebalances.put(client, ((Number) metric.metricValue()).doubleValue());
                }
            }));
        });
        return rebalances;
    }
}
