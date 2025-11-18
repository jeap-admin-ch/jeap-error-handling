package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.ErrorStubs;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.test.EmbeddedKafkaMultiClusterExtension;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.assertj.core.util.Streams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaFailedEventResenderMulticlusterIT.PORT_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(properties = {
        "jeap.messaging.kafka.embedded=false",
        "jeap.messaging.kafka.systemName=test",
        "jeap.messaging.kafka.cluster.first.bootstrapServers=localhost:" + (EmbeddedKafkaMultiClusterExtension.BASE_PORT + PORT_OFFSET),
        "jeap.messaging.kafka.cluster.first.securityProtocol=PLAINTEXT",
        "jeap.messaging.kafka.cluster.first.schemaRegistryUrl=mock://registry-outbox-1",
        "jeap.messaging.kafka.cluster.first.schemaRegistryUsername=unused",
        "jeap.messaging.kafka.cluster.first.schemaRegistryPassword=unused",
        "jeap.messaging.kafka.cluster.second.bootstrapServers=localhost:" + (EmbeddedKafkaMultiClusterExtension.BASE_PORT + PORT_OFFSET + 1),
        "jeap.messaging.kafka.cluster.second.securityProtocol=PLAINTEXT",
        "jeap.messaging.kafka.cluster.second.schemaRegistryUrl=mock://registry-outbox-2",
        "jeap.messaging.kafka.cluster.second.schemaRegistryUsername=unused",
        "jeap.messaging.kafka.cluster.second.schemaRegistryPassword=unused",
        "jeap.errorhandling.topic=errorTopic",
        "jeap.errorhandling.deadLetterTopicName=" + KafkaFailedEventResenderMulticlusterIT.DLQ_TOPIC,
        "spring.kafka.consumer.properties.max-poll-interval-ms=60000",
        "spring.kafka.consumer.properties.max-poll-records=10"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KafkaFailedEventResenderMulticlusterIT {

    static final int PORT_OFFSET = 50;
    static final String DLQ_TOPIC = "deadLetterTopic";

    private static final String FIRST_CLUSTER_NAME = "first";
    private static final String SECOND_CLUSTER_NAME = "second";

    @RegisterExtension
    static EmbeddedKafkaMultiClusterExtension embeddedKafkaMultiClusterExtension =
            EmbeddedKafkaMultiClusterExtension.withPortOffset(PORT_OFFSET);

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    protected KafkaListenerEndpointRegistry registry;
    @Autowired
    protected ErrorRepository errorRepository;
    @Autowired
    protected CausingEventRepository causingEventRepository;
    @Autowired
    protected ScheduledResendRepository scheduledResendRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    private KafkaConfiguration kafkaConfiguration;
    @Autowired
    private KafkaProperties kafkaProperties;
    @Autowired
    private KafkaFailedEventResender kafkaFailedEventResender;
    @MockitoBean
    protected KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;

    @BeforeEach
    void waitForKafka() {
        registry.getListenerContainers()
                .forEach(c -> ContainerTestUtils.waitForAssignment(c, 1));
    }

    @Test
    void testResend_WhenStoredForFirstCluster_ThenResentToFirstCluster() {
        final Error error = ErrorStubs.createTemporaryErrorWithAvroMagicBytePayload(FIRST_CLUSTER_NAME);
        assertThat(error.getCausingEvent().getMessage().getClusterName()).isEqualTo(FIRST_CLUSTER_NAME);

        kafkaFailedEventResender.resend(error);

        boolean sentToFirstCluster = hasEventBeenSentToCluster(error.getCausingEvent(), FIRST_CLUSTER_NAME);
        assertThat(sentToFirstCluster).isTrue();
    }

    @Test
    void testResend_WhenStoredForSecondCluster_ThenResentToSecondCluster() {
        final Error error = ErrorStubs.createTemporaryErrorWithAvroMagicBytePayload(SECOND_CLUSTER_NAME);
        assertThat(error.getCausingEvent().getMessage().getClusterName()).isEqualTo(SECOND_CLUSTER_NAME);

        kafkaFailedEventResender.resend(error);

        boolean sentToSecondCluster = hasEventBeenSentToCluster(error.getCausingEvent(), SECOND_CLUSTER_NAME);
        assertThat(sentToSecondCluster).isTrue();
    }

    @Test
    void testResend_WhenStoredForUnknownCluster_ThenResentToDefaultProducerCluster() {
        final String unknownClusterName = "unknownCluster";
        final Error error = ErrorStubs.createTemporaryErrorWithAvroMagicBytePayload(unknownClusterName);
        assertThat(error.getCausingEvent().getMessage().getClusterName()).isEqualTo(unknownClusterName);
        final String defaultProducerClusterName = kafkaProperties.getDefaultProducerClusterName();
        assertThat(unknownClusterName).isNotEqualTo(defaultProducerClusterName);

        kafkaFailedEventResender.resend(error);

        boolean sentToDefaultProducerCluster = hasEventBeenSentToCluster(error.getCausingEvent(), defaultProducerClusterName);
        assertThat(sentToDefaultProducerCluster).isTrue();
    }

    @Test
    void testResend_WhenStoredForDefaultCluster_ThenResentToDefaultProducerCluster() {
        final String defaultClusterName = KafkaProperties.DEFAULT_CLUSTER;
        final Error error = ErrorStubs.createTemporaryErrorWithAvroMagicBytePayload(defaultClusterName);
        assertThat(error.getCausingEvent().getMessage().getClusterName()).isEqualTo(defaultClusterName);
        final String defaultProducerClusterName = kafkaProperties.getDefaultProducerClusterName();
        assertThat(defaultClusterName).isNotEqualTo(defaultProducerClusterName);

        kafkaFailedEventResender.resend(error);

        boolean sentToDefaultProducerCluster = hasEventBeenSentToCluster(error.getCausingEvent(), defaultProducerClusterName);
        assertThat(sentToDefaultProducerCluster).isTrue();
    }

    @Test
    void testHasEventBeenSentToCluster() {
        // send an event to the first cluster
        final Error error = ErrorStubs.createTemporaryErrorWithAvroMagicBytePayload(FIRST_CLUSTER_NAME);
        assertThat(error.getCausingEvent().getMessage().getClusterName()).isEqualTo(FIRST_CLUSTER_NAME);
        kafkaFailedEventResender.resend(error);

        // The test helper method 'hasEventBeenSentToCluster' should not indicate that the event has been received on the second cluster
        boolean sentToSecondCluster = hasEventBeenSentToCluster(error.getCausingEvent(), SECOND_CLUSTER_NAME);
        assertThat(sentToSecondCluster).isFalse();

        // The test helper method 'hasEventBeenSentToCluster' should indicate that the event has been received on the first cluster
        boolean sentToFirstCluster = hasEventBeenSentToCluster(error.getCausingEvent(), FIRST_CLUSTER_NAME);
        assertThat(sentToFirstCluster).isTrue();
    }

    @AfterEach
    void cleanUp() {
        scheduledResendRepository.deleteAll();
        auditLogRepository.deleteAll();
        errorRepository.deleteAll();
        causingEventRepository.deleteAll();
    }

    private boolean hasEventBeenSentToCluster(CausingEvent causingEvent, String clusterName) {
        final String causingEventTopic = causingEvent.getMessage().getTopic();
        final byte[] causingEventPayload = causingEvent.getMessage().getPayload();
        final Consumer<Object, byte[]> consumer = createConsumer(clusterName);
        consumer.assign(Collections.singletonList(new TopicPartition(causingEventTopic, 0)));
        consumer.seekToBeginning(Collections.singletonList(new TopicPartition(causingEventTopic, 0)));
        final ConsumerRecords<Object, byte[]> records = consumer.poll(Duration.ofSeconds(10));
        consumer.close();
        return Streams.stream(records).
                map(ConsumerRecord::value).
                anyMatch(value -> Arrays.equals(value, causingEventPayload));
    }

    private Consumer<Object, byte[]> createConsumer(String clusterName) {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(clusterName));
        // The causing events created in this test are just random byte arrays, i.e. not real serialized jeap messages
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        // Remove the interceptor configured by the domain event library, because it expects messages to be jeap messages
        props.remove(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return new KafkaConsumer<>(props);
    }
}
