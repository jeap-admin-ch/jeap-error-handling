package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DeadLetterReactivationTestSupport.uniqueName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests the renewal of committed offsets on dedicated topics and consumer groups, so that the tests do not
 * interfere with each other or with the error event listeners of the EHS.
 */
class DeadLetterOffsetKeepAliveIT extends ErrorHandlingITBase {

    @Autowired
    private DeadLetterOffsetKeepAlive keepAlive;
    @Autowired
    private LockProvider lockProvider;

    private DeadLetterReactivationTestSupport support;
    private String source;
    private String target;
    private String groupId;

    @BeforeEach
    void setUp() {
        support = new DeadLetterReactivationTestSupport(kafkaConfiguration, kafkaAdmin);
        source = uniqueName("dlt");
        target = uniqueName("target");
        groupId = uniqueName("keepalive-group");
        support.createTopic(source, 1);
        support.createTopic(target, 1);
    }

    @Test
    void renewCommittedOffsets_committedOffsets_keptUnchanged() throws Exception {
        support.produce(source, 10);
        reactivate(4);
        assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 4L);

        int renewed = keepAlive.renewCommittedOffsets(groupId, source);

        assertThat(renewed).isEqualTo(1);
        assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 4L);
    }

    @Test
    void renewCommittedOffsets_noCommittedOffsets_nothingRenewed() throws Exception {
        support.produce(source, 3);

        assertThat(keepAlive.renewCommittedOffsets(groupId, source)).isZero();
        assertThat(support.committedOffsets(groupId, source)).isEmpty();
    }

    @Test
    void renewCommittedOffsets_groupSubscribedToTopic_skippedWithoutChangingOffsets() throws Exception {
        support.produce(source, 10);
        reactivate(4);

        try (AutoCloseable ignored = support.startActiveGroupMember(groupId, source)) {
            assertThat(keepAlive.renewCommittedOffsets(groupId, source)).isZero();
        }
        assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 4L);
    }

    @Test
    void renewCommittedOffsets_groupHasMembersOnOtherTopic_renewedAndMemberUndisturbed() throws Exception {
        // production situation: the group also contains the error event listener, subscribed to other topics
        String otherTopic = uniqueName("other");
        support.createTopic(otherTopic, 1);
        support.produce(source, 10);
        reactivate(4);

        try (AutoCloseable ignored = support.startActiveGroupMember(groupId, otherTopic)) {
            assertThat(keepAlive.renewCommittedOffsets(groupId, source)).isEqualTo(1);
        }
        assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 4L);
    }

    @Test
    void renewCommittedOffsets_scheduledMethodThroughShedLockProxy_doesNotThrow() {
        // calls the public, @Scheduled/@SchedulerLock-annotated method through the Spring proxy, unlike the other
        // tests here which call the package-private overload directly and so bypass ShedLock's lock configuration
        // validation (lockAtLeastFor must not exceed lockAtMostFor)
        assertThatCode(keepAlive::renewCommittedOffsets).doesNotThrowAnyException();
    }

    /**
     * Regression test for the race the dead letter topic subscribe lock fixes: without it, the keep-alive job and
     * a reactivation run could both subscribe to the dead letter topic at the same moment, and the resulting
     * rebalance could split its partitions between them - starving the reactivation run of the partitions it
     * needs, so it would end the run early reporting a (bogus) concurrent run and forward nothing, even though no
     * other reactivation was actually in progress. Holding the lock here simulates the keep-alive job being in its
     * own critical section; the concurrently started reactivation run must wait for it instead of racing it, and
     * once released, complete normally and forward every message.
     */
    @Test
    void reactivate_subscribeLockHeldByKeepAlive_waitsThenForwardsAllMessages() throws Exception {
        support.produce(source, 10);
        Optional<SimpleLock> held = DeadLetterTopicSubscribeLock.await(lockProvider);
        assertThat(held).isPresent();

        // held for much longer than a subscribe+rebalance normally takes (well under a second in these tests),
        // so the run being done early is a definite failure, not timing noise
        Duration holdDuration = Duration.ofSeconds(3);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<DeadLetterReactivationRun.Result> future = executor.submit(() ->
                    kafkaDeadLetterBatchConsumerProducer.reactivate(source, target, 4,
                            Map.of(ConsumerConfig.GROUP_ID_CONFIG, groupId), Duration.ofSeconds(30)));

            Thread.sleep(holdDuration.minusMillis(500).toMillis());
            assertThat(future.isDone()).isFalse();

            held.get().unlock();

            DeadLetterReactivationRun.Result result = future.get(35, TimeUnit.SECONDS);
            assertThat(result.forwarded()).isEqualTo(4);
            assertThat(support.committedOffsets(groupId, source)).containsEntry(0, 4L);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void consumerConfig_groupIdUsedByReactivationAndKeepAlive_isConfigured() {
        // both the reactivation consumer and the keep-alive job take the group id from this config
        assertThat(kafkaConfiguration.consumerConfig(KafkaProperties.DEFAULT_CLUSTER).get(ConsumerConfig.GROUP_ID_CONFIG))
                .isNotNull();
    }

    private void reactivate(int maxRecords) {
        kafkaDeadLetterBatchConsumerProducer.reactivate(source, target, maxRecords,
                Map.of(ConsumerConfig.GROUP_ID_CONFIG, groupId), Duration.ofSeconds(30));
    }
}
