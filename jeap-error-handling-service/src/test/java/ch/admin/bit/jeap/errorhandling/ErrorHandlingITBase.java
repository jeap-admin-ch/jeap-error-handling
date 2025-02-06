package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.crypto.api.CryptoServiceProvider;
import ch.admin.bit.jeap.crypto.api.KeyId;
import ch.admin.bit.jeap.crypto.internal.core.noop.NoopKeyIdCryptoService;
import ch.admin.bit.jeap.errorhandling.command.test.TestCommand;
import ch.admin.bit.jeap.errorhandling.event.test.TestEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.crypto.JeapKafkaAvroSerdeCryptoConfig;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.security.test.jws.JwsBuilder;
import ch.admin.bit.jeap.security.test.jws.JwsBuilderFactory;
import ch.admin.bit.jeap.security.test.resource.configuration.JeapOAuth2IntegrationTestResourceConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation.Temporality.PERMANENT;
import static ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation.Temporality.TEMPORARY;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@Slf4j
@SpringBootTest(webEnvironment = DEFINED_PORT,
        properties = {"server.port=8303",
                "jeap.errorhandling.deadLetterTopicName=" + ErrorHandlingITBase.DEAD_LETTER_TOPIC,
                "jeap.security.oauth2.resourceserver.authorization-server.issuer=" + JwsBuilder.DEFAULT_ISSUER,
                "jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri=http://localhost:${server.port}/.well-known/jwks.json"})
@AutoConfigureObservability
@Import({ErrorHandlingITBase.TestConfig.class, JeapOAuth2IntegrationTestResourceConfiguration.class})
public abstract class ErrorHandlingITBase extends KafkaIntegrationTestBase {
    protected static final String DOMAIN_EVENT_TOPIC = "originalTopic";
    protected static final String COMMAND_TOPIC = "commandTopic";
    protected static final String DEAD_LETTER_TOPIC = "dltTopic";
    protected static final String ERROR_TOPIC = "errorTopic";
    protected static final String TEMPORARY_ERROR = "temp";
    protected static final String PERMANENT_ERROR = "perm";
    protected static final String RETRY_SUCCESS = "retry_success";
    protected static final Duration FORTY_SECONDS = Duration.ofSeconds(40);

    @Autowired
    protected KafkaListenerEndpointRegistry registry;
    @Autowired
    protected ErrorRepository errorRepository;
    @Autowired
    protected ErrorGroupRepository errorGroupRepository;
    @Autowired
    protected CausingEventRepository causingEventRepository;
    @Autowired
    protected ScheduledResendRepository scheduledResendRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    protected KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;
    @Autowired
    protected TestConsumer testConsumer;
    @Autowired
    protected JwsBuilderFactory jwsBuilderFactory;
    @Autowired
    protected KafkaConfiguration kafkaConfiguration;
    @Autowired
    protected KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;

    @AfterEach
    @BeforeEach
    void clearRepository() {
        log.info("Clearing repositories");
        scheduledResendRepository.deleteAll();
        auditLogRepository.deleteAll();
        errorRepository.deleteAll();
        errorGroupRepository.deleteAll();
        causingEventRepository.deleteAll();
        testConsumer.reset();
    }

    // Providing a NOOP crypto setup to be able to set the "encrypted message" header on messages of the type TestMessage in order
    // to test that this header is propagated to resent messages properly (testRetry_verifyOriginalHeaderIsSentToConsumerAgain).
    static class TestConfig {
        @Bean
        @Primary
        JeapKafkaAvroSerdeCryptoConfig jeapKafkaAvroSerdeCryptoConfig() {
            return new JeapKafkaAvroSerdeCryptoConfig(
                    new CryptoServiceProvider(List.of(new NoopKeyIdCryptoService(Set.of("testkeyid")))),
                    Map.of("TestMessage", new KeyId("testkeyid")));
        }

        @Bean
        TestConsumer testConsumer() {
            return new TestConsumer();
        }
    }

    protected static class TestConsumer {

        private final List<TestEvent> consumedEvents = new ArrayList<>();
        private final List<ConsumerRecord<AvroMessageKey, TestEvent>> consumedRecords = new ArrayList<>();
        private final List<TestCommand> consumedCommands = new ArrayList<>();
        @Getter
        private boolean successfulRetrySimulated;

        @TestKafkaListener(topics = {DOMAIN_EVENT_TOPIC}, id = "test-consumer")
        public void consume(final ConsumerRecord<AvroMessageKey, TestEvent> testEventConsumerRecord) {
            TestEvent testEvent = testEventConsumerRecord.value();
            consumedEvents.add(testEvent);
            consumedRecords.add(testEventConsumerRecord);

            String payloadMessage = testEvent.getPayload().getMessage();
            log.info("Consuming message in TestConsumer: {}", payloadMessage);
            simulateError(payloadMessage);
        }

        @TestKafkaListener(topics = {COMMAND_TOPIC}, id = "test-command-consumer")
        public void consume(final TestCommand testCommand) {
            consumedCommands.add(testCommand);

            String payloadMessage = testCommand.getPayload().getMessage();
            log.info("Consuming command in T            return null;estConsumer: {}", payloadMessage);
            simulateError(payloadMessage);
        }

        private void simulateError(String payloadMessage) {
            if (TEMPORARY_ERROR.equals(payloadMessage)) {
                throw new TestMessageProcessingException(TEMPORARY, "500", payloadMessage);
            } else if (PERMANENT_ERROR.equals(payloadMessage)) {
                throw new TestMessageProcessingException(PERMANENT, "404", payloadMessage);
            } else if (RETRY_SUCCESS.equals(payloadMessage)) {
                boolean isFirstMessage = (consumedEvents.size() + consumedCommands.size()) == 1;
                if (isFirstMessage) {
                    throw new TestMessageProcessingException(PERMANENT, "404", payloadMessage);
                } else {
                    successfulRetrySimulated = true;
                    return; // ack
                }
            }
            throw new IllegalStateException("Unexpected error");
        }

        public void reset() {
            consumedEvents.clear();
            consumedCommands.clear();
            consumedRecords.clear();
            successfulRetrySimulated = false;
        }

        public List<TestEvent> getConsumedEventsByIdempotenceId(String idempotenceId) {
            return consumedEvents.stream()
                    .filter(event -> idempotenceId.equals(event.getIdentity().getIdempotenceId()))
                    .toList();
        }

        public List<TestCommand> getConsumedCommandsByIdempotenceId(String idempotenceId) {
            return consumedCommands.stream()
                    .filter(event -> idempotenceId.equals(event.getIdentity().getIdempotenceId()))
                    .toList();
        }

        public List<ConsumerRecord<AvroMessageKey, TestEvent>> getConsumedRecordsByIdempotenceId(String idempotenceId) {
            return consumedRecords.stream()
                    .filter(record -> idempotenceId.equals(record.value().getIdentity().getIdempotenceId()))
                    .toList();

        }
    }
}
