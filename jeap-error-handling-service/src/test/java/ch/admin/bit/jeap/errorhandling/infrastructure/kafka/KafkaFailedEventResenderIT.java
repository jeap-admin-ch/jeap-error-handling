package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.ErrorStubs;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.MessageHeader;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.OriginalTraceContext;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContext;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextProvider;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextScope;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextUpdater;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.assertj.core.util.Streams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("resource")
class KafkaFailedEventResenderIT extends ErrorHandlingITBase {

    @Autowired
    private KafkaFailedEventResender kafkaFailedEventResender;

    @Autowired
    private TraceContextUpdater traceContextUpdater;

    @Autowired
    private TraceContextProvider traceContextProvider;

    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    private AdminClient adminClient;

    @Test
    void resend_noOriginalTraceIdFound_traceIdFromContextForwarded() {
        //given
        final Error temporaryError = ErrorStubs.createTemporaryError();
        final long traceId = new Random().nextLong();
        final long traceIdHigh = new Random().nextLong();
        final long spanId = new Random().nextLong();
        ReflectionTestUtils.setField(temporaryError, "originalTraceContext", null);

        // Activate a current tracing context with the desired trace id; resend should adopt it for the outgoing
        // producer span (no OriginalTraceContext on the error, so fallback = current context).
        try (TraceContextScope _ = traceContextUpdater.setTraceContext(
                new TraceContext(traceIdHigh, traceId, spanId, null, null, Boolean.TRUE))) {

            final String expectedTraceIdHex = toHex32(traceIdHigh, traceId);

            //when
            kafkaFailedEventResender.resend(temporaryError);

            //then
            assertThat(temporaryError.getOriginalTraceContext()).isNull();
            assertThat(retrieveHeaderFromConsumedMessage(traceIdHigh)).isEqualTo(expectedTraceIdHex);
        }
    }

    @Test
    void resend_originalTraceIdFound_originalTraceIdFromErrorForwarded() {
        //given
        final Error temporaryError = ErrorStubs.createTemporaryError();
        final long traceIdHigh = new Random().nextLong();
        final long traceId = new Random().nextLong();
        final long spanId = new Random().nextLong();
        ReflectionTestUtils.setField(temporaryError, "originalTraceContext", OriginalTraceContext.builder()
                .traceIdHigh(traceIdHigh)
                .traceId(traceId)
                .spanId(spanId)
                .traceIdString(toHex32(traceIdHigh, traceId))
                .build());

        long currentTraceIdHigh = new Random().nextLong();
        long currentTraceId = new Random().nextLong();
        long currentSpanId = new Random().nextLong();

        // Activate a DIFFERENT current context to prove that the error's OriginalTraceContext wins over the current.
        try (TraceContextScope _ = traceContextUpdater.setTraceContext(
                new TraceContext(currentTraceIdHigh, currentTraceId, currentSpanId, null, null, Boolean.TRUE))) {

            //when
            kafkaFailedEventResender.resend(temporaryError);

            //then
            assertThat(temporaryError.getOriginalTraceContext().getTraceIdHigh()).isEqualTo(traceIdHigh);
            assertThat(temporaryError.getOriginalTraceContext().getTraceId()).isEqualTo(traceId);
            assertThat(retrieveHeaderFromConsumedMessage(traceIdHigh))
                    .isEqualTo(toHex32(traceIdHigh, traceId));
            assertThat(traceContextProvider.getTraceContext().getTraceIdString())
                    .as("Resend must close the temporary original-trace scope and restore the previously active trace.")
                    .isEqualTo(toHex32(currentTraceIdHigh, currentTraceId));
        }
    }

    @Test
    void resend_originalTraceContextSampledFalse_traceparentFlagsByteIsZero() {
        // given an error that was originally received in an unsampled trace
        final Error temporaryError = ErrorStubs.createTemporaryError();
        final long traceIdHigh = new Random().nextLong();
        final long traceId = new Random().nextLong();
        final long spanId = new Random().nextLong();
        ReflectionTestUtils.setField(temporaryError, "originalTraceContext", OriginalTraceContext.builder()
                .traceIdHigh(traceIdHigh)
                .traceId(traceId)
                .spanId(spanId)
                .traceIdString(toHex32(traceIdHigh, traceId))
                .sampled(false)
                .build());

        // Activate a SAMPLED current context to prove the resend honors the original (unsampled) decision
        // rather than falling back to the current trace's sampling flag.
        try (TraceContextScope _ = traceContextUpdater.setTraceContext(
                new TraceContext(new Random().nextLong(), new Random().nextLong(), new Random().nextLong(),
                        null, null, Boolean.TRUE))) {

            // when
            kafkaFailedEventResender.resend(temporaryError);
        }

        // then: traceparent layout is "00-<32-hex traceId>-<16-hex spanId>-<2-hex flags>"; flags must be "00".
        String[] traceparentParts = retrieveTraceparentFromConsumedMessage(traceIdHigh).split("-");
        assertThat(traceparentParts)
                .as("traceparent must have the 4 W3C segments: version, trace-id, parent-id, flags")
                .hasSize(4);
        assertThat(traceparentParts[1])
                .as("traceparent trace-id segment must equal the original trace id")
                .isEqualTo(toHex32(traceIdHigh, traceId));
        assertThat(traceparentParts[3])
                .as("traceparent flags must be 00 when the original trace context is unsampled")
                .isEqualTo("00");
    }

    @Test
    void testResend_WhenEventStoredForUnknownClusterName_ThenEventWillBeSentToTheSingleConfiguredClusterAutomatically() {
        final String unknownClusterName = "unknown";
        assertThat(unknownClusterName).isNotEqualTo(kafkaProperties.getDefaultProducerClusterName());
        final Error error = ErrorStubs.createTemporaryErrorWithAvroMagicBytePayload(unknownClusterName);

        kafkaFailedEventResender.resend(error);

        assertThat(hasEventBeenResent(error.getCausingEvent())).isTrue();
    }

    @Test
    void testResend_serviceHeadersMustBeSetOnceIfResent() {
        final Error error = ErrorStubs.createTemporaryError(ErrorStubs.ERROR_EVENT_PUBLISHER_SERVICE, ErrorStubs.CAUSING_EVENT_PUBLISHER_SERVICE);

        kafkaFailedEventResender.resend(error);

        ConsumerRecords<Object, Object> consumerRecords = consumeAllEvents();
        assertEquals(1, consumerRecords.count());
        for (ConsumerRecord<Object, Object> consumerRecord : consumerRecords) {
            assertHeaders(consumerRecord, "jeap_eh_target_service", 1, ErrorStubs.ERROR_EVENT_PUBLISHER_SERVICE);
            assertHeaders(consumerRecord, "jeap_eh_error_handling_service", 1, "jeap-error-handling-service");
        }
    }

    @Test
    void testResendSeveralTime_serviceHeadersMustBeSetOnceIfResent() {
        final Error error = ErrorStubs.createTemporaryErrorWithHeaders(
                ErrorStubs.ERROR_EVENT_PUBLISHER_SERVICE, ErrorStubs.CAUSING_EVENT_PUBLISHER_SERVICE,
                MessageHeader.builder()
                        .headerName("jeap_eh_target_service")
                        .headerValue("dummy-service".getBytes(StandardCharsets.UTF_8))
                        .build(),
                MessageHeader.builder()
                        .headerName("jeap_eh_error_handling_service")
                        .headerValue("dummy-eh-service".getBytes(StandardCharsets.UTF_8))
                        .build()
        );

        kafkaFailedEventResender.resend(error);
        kafkaFailedEventResender.resend(error);
        kafkaFailedEventResender.resend(error);

        ConsumerRecords<Object, Object> consumerRecords = consumeAllEvents();
        assertEquals(3, consumerRecords.count());
        for (ConsumerRecord<Object, Object> consumerRecord : consumerRecords) {
            assertHeaders(consumerRecord, "jeap_eh_target_service", 1, ErrorStubs.ERROR_EVENT_PUBLISHER_SERVICE);
            assertHeaders(consumerRecord, "jeap_eh_error_handling_service", 1, "jeap-error-handling-service");
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void assertHeaders(ConsumerRecord<Object, Object> consumerRecord, String headerName, int expectedCount, String expectedValue) {
        Iterable<Header> allHeaders = consumerRecord.headers().headers(headerName);
        int headersCount = 0;
        for (Header header : allHeaders) {
            headersCount++;
            assertEquals(expectedValue, new String(header.value(), StandardCharsets.UTF_8));
        }
        assertEquals(expectedCount, headersCount);
        assertEquals(expectedValue, new String(consumerRecord.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());

        // Delete and recreate topic
        try {
            adminClient.deleteTopics(Collections.singletonList("topic")).all().get();
        } catch (Exception e) {
            // Topic might not exist yet
        }
    }

    @AfterEach
    void cleanUp() {
        scheduledResendRepository.deleteAll();
        auditLogRepository.deleteAll();
        errorRepository.deleteAll();
        causingEventRepository.deleteAll();
        if (adminClient != null) {
            adminClient.close();
        }
    }

    private String retrieveHeaderFromConsumedMessage(long traceIdHigh) {
        return retrieveTraceparentFromConsumedMessage(traceIdHigh).split("-")[1];
    }

    private String retrieveTraceparentFromConsumedMessage(long traceIdHigh) {
        String expectedTraceIdPrefix = String.format("%016x", traceIdHigh);
        return Streams.stream(consumeAllEvents())
                .filter(consumerRecord -> consumerRecord.headers().lastHeader("traceparent") != null
                        && consumerRecord.headers().lastHeader("traceparent").value() != null)
                .map(consumerRecord -> new String(consumerRecord.headers().lastHeader("traceparent").value()))
                .filter(traceparent -> traceparent.startsWith("00-" + expectedTraceIdPrefix))
                .findFirst().orElseThrow();
    }

    private static String toHex32(long high, long low) {
        return String.format("%016x%016x", high, low);
    }

    private boolean hasEventBeenResent(CausingEvent causingEvent) {
        return Streams.stream(consumeAllEvents()).
                map(ConsumerRecord::value).
                filter(byte[].class::isInstance).
                map(v -> (byte[]) v).
                anyMatch(value -> Arrays.equals(value, causingEvent.getMessage().getPayload()));
    }

    private ConsumerRecords<Object, Object> consumeAllEvents() {
        final Consumer<Object, Object> consumer = createConsumer();
        consumer.assign(Collections.singletonList(new TopicPartition("topic", 0)));
        consumer.seekToBeginning(Collections.singletonList(new TopicPartition("topic", 0)));
        final ConsumerRecords<Object, Object> records = consumer.poll(Duration.ofSeconds(10));
        consumer.close();
        return records;
    }

    private Consumer<Object, Object> createConsumer() {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.consumerConfig(KafkaProperties.DEFAULT_CLUSTER));
        // We are resending messages exactly as received i.e. as byte array of the original message
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        // Remove the interceptor configured by the domain event library, because it expects messages to be domain events.
        props.remove(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return new KafkaConsumer<>(props);
    }
}
