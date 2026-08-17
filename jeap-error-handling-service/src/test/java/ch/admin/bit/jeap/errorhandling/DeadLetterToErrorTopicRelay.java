package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedReferences;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageReference;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Test-only relay that hands the failed events published to the dead letter topic to the topic the error
 * handling service consumes from.
 * <p>
 * In production the two topics belong to different applications: the message consumers of a system publish
 * their failures to the error topic ({@code jeap.messaging.kafka.errorTopicName}), the error handling service
 * consumes that topic ({@code jeap.errorhandling.topic}) and publishes its own failures to the dead letter
 * topic ({@code jeap.errorhandling.deadLetterTopicName}). In an integration test the failing test consumer
 * shares the application context - and therefore the error topic, which
 * {@code DeadLetterTopicNameEnvPostProcessor} pins to the dead letter topic - with the error handling service.
 * This relay bridges that gap: it takes the failures the test consumer produced to the dead letter topic and
 * hands them to the error handling service on its input topic, as a separate application would.
 * <p>
 * The relay consumes and publishes with the Kafka clients managed by Spring, so its consumer is stopped and
 * its producer is closed with the application context. The relayed failed event is republished as it was
 * consumed; the headers of the originally failed message are part of its payload and are therefore preserved.
 * <p>
 * Failures of the error handling service itself are not relayed - they stay in the dead letter topic as they
 * do in production, which also rules out an endless relay loop.
 * <p>
 * Activated with the profile {@value #PROFILE}.
 */
@Slf4j
@Component
@Profile(DeadLetterToErrorTopicRelay.PROFILE)
class DeadLetterToErrorTopicRelay {

    static final String PROFILE = "dead-letter-relay";

    private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;
    private final String errorTopicName;
    private final String deadLetterTopicName;

    DeadLetterToErrorTopicRelay(KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate,
                                @Value("${jeap.errorhandling.topic}") String errorTopicName,
                                @Value("${jeap.errorhandling.deadLetterTopicName}") String deadLetterTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.errorTopicName = errorTopicName;
        this.deadLetterTopicName = deadLetterTopicName;
    }

    @TestKafkaListener(topics = "${jeap.errorhandling.deadLetterTopicName}", groupId = "dead-letter-relay")
    void relay(ConsumerRecord<AvroMessageKey, MessageProcessingFailedEvent> record) {
        MessageProcessingFailedEvent failedEvent = record.value();
        Optional<String> failedMessageTopic = failedMessageTopic(failedEvent);
        if (failedMessageTopic.filter(this::isTopicOfErrorHandlingService).isPresent()) {
            log.info("Not relaying the failure of the error handling service itself on topic {} from partition {} with offset {}",
                    failedMessageTopic.get(), record.partition(), record.offset());
            return;
        }
        log.info("Relaying record from partition {} with offset {} to topic {}",
                record.partition(), record.offset(), errorTopicName);
        // The failed events are published without a key, and the key deserializer materializes a placeholder
        // key that cannot be serialized again - so the event is relayed without a key, as it was published.
        kafkaTemplate.send(errorTopicName, failedEvent);
    }

    /**
     * Whether the given topic is one of the topics of the error handling service itself: a failed event
     * reporting on a message from one of them reports a failure of the error handling service. Such failures
     * stay in the dead letter topic as they do in production, which also rules out an endless relay loop.
     */
    private boolean isTopicOfErrorHandlingService(String topicName) {
        return topicName.equals(errorTopicName) || topicName.equals(deadLetterTopicName);
    }

    /**
     * The topic the message that the given failed event reports on was consumed from, if the failed event
     * carries a reference to that message.
     */
    private Optional<String> failedMessageTopic(MessageProcessingFailedEvent failedEvent) {
        return Optional.ofNullable(failedEvent.getReferences())
                .flatMap(MessageProcessingFailedReferences::getOptionalMessage)
                .flatMap(MessageReference::getOptionalTopicName);
    }
}
