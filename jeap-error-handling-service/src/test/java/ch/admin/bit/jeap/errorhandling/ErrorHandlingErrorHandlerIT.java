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
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.messaging.support.GenericMessage;
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
import java.util.concurrent.ExecutionException;

import static ch.admin.bit.jeap.errorhandling.ErrorHandlingErrorHandlerIT.DLT_TOPIC;
import static ch.admin.bit.jeap.errorhandling.ErrorHandlingErrorHandlerIT.ERROR_HANDLING_SERVICE_TOPIC;
import static ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation.Temporality.PERMANENT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@ActiveProfiles("error-handler-it")
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "jeap.errorhandling.topic=" + ERROR_HANDLING_SERVICE_TOPIC,
        "jeap.errorhandling.deadLetterTopicName=" + DLT_TOPIC})
@Import({JeapOAuth2IntegrationTestResourceConfiguration.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfigureObservability
class ErrorHandlingErrorHandlerIT extends KafkaIntegrationTestBase {

    static final String ERROR_HANDLING_SERVICE_TOPIC = "consumer-topic";
    static final String DLT_TOPIC = "errorTopic";
    private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);
    @Autowired
    private KafkaListenerEndpointRegistry registry;

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
        final ProducerRecord<String, String> record = new ProducerRecord<>(ERROR_HANDLING_SERVICE_TOPIC, "fake Event");

        // when
        producer.send(record).get();

        // then
        await("event is sent to DLT 1 time").atMost(THIRTY_SECONDS).until(() -> dltConsumer.getConsumedMessages().size() == 1);

        GenericMessage<?> genericMessage = dltConsumer.getConsumedMessages().getFirst();
        assertInstanceOf(MessageProcessingFailedEvent.class, genericMessage.getPayload());
        assertEquals("java.lang.Exception: Could not deserialize value", ((MessageProcessingFailedEvent) genericMessage.getPayload()).getPayload().getErrorMessage());
        assertEquals("fake Event", StandardCharsets.UTF_8.decode(((MessageProcessingFailedEvent) genericMessage.getPayload()).getPayload().getOriginalMessage()).toString());
        producer.close();
    }

    @Test
    void testCanConsumeInvalidAvroMessageEvent() throws ExecutionException, InterruptedException {
        // given
        final Producer<String, AvroMessage> producer = createAvroMessageProducer();
        final ProducerRecord<String, AvroMessage> record = new ProducerRecord<>(ERROR_HANDLING_SERVICE_TOPIC, createTestEvent("Content Test Event"));

        // when
        producer.send(record).get();

        // then
        await("event is sent to DLT 1 time").atMost(THIRTY_SECONDS).until(() -> dltConsumer.getConsumedMessages().size() == 1);

        GenericMessage<?> genericMessage = dltConsumer.getConsumedMessages().getFirst();
        assertInstanceOf(MessageProcessingFailedEvent.class, genericMessage.getPayload());
        assertTrue(((MessageProcessingFailedEvent) genericMessage.getPayload()).getPayload().getErrorMessage().contains("TestEvent cannot be cast to class"));
        assertTrue(StandardCharsets.UTF_8.decode(((MessageProcessingFailedEvent) genericMessage.getPayload()).getPayload().getOriginalMessage()).toString().contains("Content Test Event"));
        producer.close();
    }

    @Test
    void testCanConsumeValidMessage_nonAvroOriginalEvent_isPublishedToDlt() throws ExecutionException, InterruptedException {
        // given
        final Producer<String, AvroMessage> producer = createAvroMessageProducer();
        final ProducerRecord<String, AvroMessage> record = new ProducerRecord<>(ERROR_HANDLING_SERVICE_TOPIC, createMessageProcessingFailedEventWithOriginalMessageStringPayload());

        // when
        producer.send(record).get();

        // then
        await("event is sent to DLT 1 time").atMost(THIRTY_SECONDS)
                .until(() -> dltConsumer.getConsumedMessages().size() == 1);

        GenericMessage<?> genericMessage = dltConsumer.getConsumedMessages().getFirst();
        assertInstanceOf(MessageProcessingFailedEvent.class, genericMessage.getPayload());
        assertTrue(((MessageProcessingFailedEvent) genericMessage.getPayload()).getPayload().getErrorMessage()
                .contains("Not an Avro message"));
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
        ConsumerRecord<?, ?> record = new ConsumerRecord<>("Topic", 1, 1, null, "original non-avro payload");
        TestMessageProcessingException eventHandleException = new TestMessageProcessingException(PERMANENT, "500", "Payload");
        return MessageProcessingFailedEventBuilder.create()
                .eventHandleException(eventHandleException)
                .serviceName("service")
                .systemName("system")
                .originalMessage(record, null)
                .build();
    }

    //@formatter:on

    @BeforeEach
    void waitForKafkaListener() {
        MessageListenerContainer container = registry.getListenerContainers().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No event listener found"));
        ContainerTestUtils.waitForAssignment(container, 1);
    }

    @AfterEach
    void clearRepository() {
        dltConsumer.reset();
    }

    @Getter
    @Component
    @Profile("error-handler-it")
    static class DLTConsumer {

        private final List<GenericMessage<?>> consumedMessages = new ArrayList<>();

        @TestKafkaListener(topics = {DLT_TOPIC}, groupId = "dlt-consumer")
        public void consume(final GenericMessage<?> message) {
            consumedMessages.add(message);

            log.info("Consuming message in DLTConsumer: {}", message);
        }

        private void reset() {
            consumedMessages.clear();
        }
    }
}
