package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Serializes becoming a transient member of the dead letter topic's consumer group between
 * {@link DeadLetterOffsetKeepAlive} and {@link DeadLetterReactivationRun}. Both join the group briefly to
 * subscribe to the dead letter topic, and if they did so at the same moment, the resulting rebalance could split
 * the topic's partitions between them instead of assigning them all to one, silently starving whichever one loses
 * out. Holding this lock for the short window from subscribing to the first resulting assignment - not for the
 * whole run/job - prevents that, while leaving them free to run concurrently with each other and with everything
 * else the rest of the time.
 */
final class DeadLetterTopicSubscribeLock {

    private static final String LOCK_NAME = "dead-letter-topic-subscribe";
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(200);

    // the longest either side should ever hold the lock for: waiting for the first assignment after subscribing
    static final Duration MAX_DURATION = Duration.ofSeconds(30);

    private DeadLetterTopicSubscribeLock() {
    }

    /**
     * Waits up to {@link #MAX_DURATION} for the lock, since that is also the longest the current holder should
     * take to release it.
     *
     * @return the lock, or empty if it could not be acquired within that time
     */
    static Optional<SimpleLock> await(LockProvider lockProvider) {
        long deadline = System.nanoTime() + MAX_DURATION.toNanos();
        while (true) {
            Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(Instant.now(), LOCK_NAME, MAX_DURATION, Duration.ZERO));
            if (lock.isPresent()) {
                return lock;
            }
            if (System.nanoTime() >= deadline || !sleep()) {
                return Optional.empty();
            }
        }
    }

    private static boolean sleep() {
        try {
            Thread.sleep(RETRY_INTERVAL.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
