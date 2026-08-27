package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventIdentity;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventPublisher;
import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventType;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.event.test.TestPayload;
import ch.admin.bit.jeap.errorhandling.event.test.TestReferences;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.security.test.resource.configuration.JeapOAuth2IntegrationTestResourceConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import static ch.admin.bit.jeap.errorhandling.ErrorHandlingErrorHandlerIT.DLT_TOPIC;
import static ch.admin.bit.jeap.errorhandling.ErrorHandlingErrorHandlerIT.ERROR_HANDLING_SERVICE_TOPIC;
import static ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation.Temporality.PERMANENT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@ActiveProfiles("error-handler-it")
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "jeap.errorhandling.topic=" + ERROR_HANDLING_SERVICE_TOPIC,
        "jeap.errorhandling.deadLetterTopicName=" + DLT_TOPIC})
@Import({JeapOAuth2IntegrationTestResourceConfiguration.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfigureMetrics
@AutoConfigureTracing
class ErrorHandlingErrorHandlerIT extends KafkaIntegrationTestBase {

    static final String ERROR_HANDLING_SERVICE_TOPIC = "consumer-topic";
    static final String DLT_TOPIC = "errorTopic";
    private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);
    @Autowired
    private KafkaListenerEndpointRegistry registry;

    /**
     * The listener containers registered as beans, i.e. the container the error handling service consumes the
     * failed events with - the containers of the Kafka listener endpoints are not beans and are found in the
     * {@link KafkaListenerEndpointRegistry} instead.
     */
    @Autowired
    private List<MessageListenerContainer> messageListenerContainers;

    @Autowired
    private DLTConsumer dltConsumer;

    @Autowired
    protected KafkaAdmin kafkaAdmin;

    @MockitoBean
    protected KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;

    private static TestEvent createTestEvent(String messagePayload) {
        return TestEvent.newBuilder()
                .setType(AvroDomainEventType.newBuilder()
                        .setName("TestEvent")
                        .setVersion("1")
                        .build())
                .setReferences(TestReferences.newBuilder().build())
                .setDomainEventVersion("1.0.0")
                .setIdentity(AvroDomainEventIdentity.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setIdempotenceId(UUID.randomUUID().toString())
                        .setCreated(Instant.now())
                        .build())
                .setPayload(TestPayload.newBuilder()
                        .setMessage(messagePayload)
                        .build())
                .setPublisher(AvroDomainEventPublisher.newBuilder()
                        .setSystem("TEST")
                        .setService("test-service")
                        .build())
                .build();
    }

    @Test
    void testCanConsumeInvalidEvent() throws ExecutionException, InterruptedException {
        // given
        final Producer<String, String> producer = createStringMessageProducer();
        final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(ERROR_HANDLING_SERVICE_TOPIC, "fake Event");

        // when
        producer.send(producerRecord).get();

        // then
        await("event is sent to DLT").atMost(THIRTY_SECONDS)
                .until(() -> dltConsumer.getMessageWithOriginalMessage("fake Event") != null);

        MessageProcessingFailedEvent genericMessage = dltConsumer.getMessageWithOriginalMessage("fake Event");
        assertEquals("java.lang.Exception: Could not deserialize value", genericMessage.getPayload().getErrorMessage());
        producer.close();
    }

    @Test
    void testCanConsumeInvalidAvroMessageEvent() throws ExecutionException, InterruptedException {
        // given
        final TestEvent testEvent = createTestEvent("Content Test Event");
        final String testEventIdempotenceId = testEvent.getIdentity().getIdempotenceId();
        final Producer<String, AvroMessage> producer = createAvroMessageProducer();
        final ProducerRecord<String, AvroMessage> producerRecord = new ProducerRecord<>(ERROR_HANDLING_SERVICE_TOPIC, testEvent);

        // when
        producer.send(producerRecord).get();

        // then
        await("event is sent to DLT 1 time").atMost(THIRTY_SECONDS).until(() -> dltConsumer.hasMessageWithIdempotenceId(testEventIdempotenceId));

        MessageProcessingFailedEvent genericMessage = dltConsumer.getMessageWithIdempotenceId(testEventIdempotenceId);
        assertTrue(genericMessage.getPayload().getErrorMessage().contains("Unsupported error event type"));
        assertTrue(StandardCharsets.UTF_8.decode(genericMessage.getPayload().getOriginalMessage()).toString().contains("Content Test Event"));
        producer.close();
    }

    @Test
    void testCanConsumeValidMessage_nonAvroOriginalEvent_isPublishedToDlt() throws ExecutionException, InterruptedException {
        // given
        final Producer<String, AvroMessage> producer = createAvroMessageProducer();
        final MessageProcessingFailedEvent message = createMessageProcessingFailedEventWithOriginalMessageStringPayload();
        final String testEventIdempotenceId = message.getIdentity().getIdempotenceId();
        final ProducerRecord<String, AvroMessage> producerRecord = new ProducerRecord<>(ERROR_HANDLING_SERVICE_TOPIC, message);

        // when
        producer.send(producerRecord).get();

        // then
        await("event is sent to DLT 1 time").atMost(THIRTY_SECONDS).until(() -> dltConsumer.hasMessageWithIdempotenceId(testEventIdempotenceId));

        MessageProcessingFailedEvent genericMessage = dltConsumer.getMessageWithIdempotenceId(testEventIdempotenceId);
        assertTrue(genericMessage.getPayload().getErrorMessage().contains("Not an Avro message"));
        producer.close();
    }

    private Producer<String, String> createStringMessageProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAdmin.getConfigurationProperties().get("bootstrap.servers"));
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "jEAPErrorHandlingFakeStringProducer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private Producer<String, AvroMessage> createAvroMessageProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAdmin.getConfigurationProperties().get("bootstrap.servers"));
        props.put("schema.registry.url", "mock://none");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "jEAPErrorHandlingFakeAvroMessageProducer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CustomKafkaAvroSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private MessageProcessingFailedEvent createMessageProcessingFailedEventWithOriginalMessageStringPayload() {
        ConsumerRecord<?, ?> consumerRecord = new ConsumerRecord<>("Topic", 1, 1, null, "original non-avro payload");
        TestMessageProcessingException eventHandleException = new TestMessageProcessingException(PERMANENT, "500", "Payload");
        return MessageProcessingFailedEventBuilder.create()
                .eventHandleException(eventHandleException)
                .serviceName("service")
                .systemName("system")
                .originalMessage(consumerRecord, null)
                .build();
    }

    //@formatter:on

    @BeforeEach
    void waitForKafkaListeners() {
        // Wait for every listener, not just for an arbitrary one: as long as a consumer has not been assigned
        // its partition, it misses the messages published to its topic, because it starts reading at the end
        // of the topic. This holds for the DLT consumer of this test as well as for the listener of the error
        // handling service itself, which is not registered as a Kafka listener endpoint but as a container
        // bean, see KafkaMessageProcessingFailedEventConsumerFactory.
        List<MessageListenerContainer> containers = new ArrayList<>(registry.getListenerContainers());
        containers.addAll(messageListenerContainers);
        if (containers.isEmpty()) {
            throw new IllegalStateException("No event listener found");
        }
        containers.forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @AfterEach
    void clearRepository() {
        dltConsumer.reset();
    }

    @Getter
    @Component
    @Profile("error-handler-it")
    static class DLTConsumer {

        // written by the Kafka listener thread and read by the test thread
        private final List<MessageProcessingFailedEvent> consumedMessages = new CopyOnWriteArrayList<>();

        @TestKafkaListener(topics = {DLT_TOPIC}, groupId = "dlt-consumer")
        public void consume(final MessageProcessingFailedEvent message) {
            consumedMessages.add(message);

            log.info("Consuming message in DLTConsumer: {}", message);
        }

        boolean hasMessageWithIdempotenceId(String idempotenceId) {
            return getMessageWithIdempotenceId(idempotenceId) != null;
        }

        /**
         * Looks up a consumed message by its original message, for messages that failed to deserialize and
         * therefore carry no metadata of the failed message.
         */
        MessageProcessingFailedEvent getMessageWithOriginalMessage(String originalMessage) {
            return consumedMessages.stream()
                    .filter(message -> message.getPayload().getOriginalMessage() != null)
                    .filter(message -> originalMessage.equals(
                            StandardCharsets.UTF_8.decode(message.getPayload().getOriginalMessage().duplicate()).toString()))
                    .findFirst()
                    .orElse(null);
        }

        MessageProcessingFailedEvent getMessageWithIdempotenceId(String idempotenceId) {
            // messages that failed to deserialize carry no metadata of the failed message
            return consumedMessages.stream()
                    .filter(message -> message.getPayload().getFailedMessageMetadata() != null)
                    .filter(message -> idempotenceId.equals(message.getPayload().getFailedMessageMetadata().getIdempotenceId()))
                    .findFirst()
                    .orElse(null);
        }

        private void reset() {
            consumedMessages.clear();
        }
    }
}
