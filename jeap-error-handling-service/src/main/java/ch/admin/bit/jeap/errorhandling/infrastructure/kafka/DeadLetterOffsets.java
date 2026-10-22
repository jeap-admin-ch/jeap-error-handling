package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;

/**
 * Offset math shared by {@link DeadLetterReactivationRun} and {@link DeadLetterTopicMetrics}, both of which
 * determine how far a consumer group has progressed on the dead letter topic.
 */
final class DeadLetterOffsets {

    private DeadLetterOffsets() {
    }

    /**
     * The position a consumer of the group starts at: the committed offset, or the earliest available offset if
     * there is no committed offset or the committed offset is no longer available.
     */
    static long committedPosition(Map<TopicPartition, Long> beginningOffsets, TopicPartition partition, OffsetAndMetadata committed) {
        long beginning = beginningOffsets.getOrDefault(partition, 0L);
        return committed == null ? beginning : Math.max(committed.offset(), beginning);
    }
}
