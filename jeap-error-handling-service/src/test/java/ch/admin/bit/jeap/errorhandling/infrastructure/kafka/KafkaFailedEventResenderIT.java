package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import brave.internal.codec.HexCodec;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.TraceContext;
import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.ErrorStubs;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.OriginalTraceContext;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.assertj.core.util.Streams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("resource")
class KafkaFailedEventResenderIT extends ErrorHandlingITBase {

    @Autowired
    private KafkaFailedEventResender kafkaFailedEventResender;

    @Autowired
    private KafkaTracing kafkaTracing;

    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Test
    void resend_noOriginalTraceIdFound_traceIdFromContextForwarded() {
        //given
        final Error temporaryError = ErrorStubs.createTemporaryError();
        final long traceId = new Random().nextLong();
        final long traceIdHigh = new Random().nextLong();
        ReflectionTestUtils.setField(temporaryError, "originalTraceContext", null);
        kafkaTracing.messagingTracing().tracing().currentTraceContext().newScope(
                TraceContext
                        .newBuilder()
                        .spanId(new Random().nextLong())
                        .traceIdHigh(traceIdHigh)
                        .traceId(traceId)
                        .build());

        final String traceIdString = kafkaTracing.messagingTracing().tracing().currentTraceContext().get().traceIdString();

        //when
        kafkaFailedEventResender.resend(temporaryError);

        //then
        assertThat(temporaryError.getOriginalTraceContext()).isNull();
        assertThat(retrieveHeaderFromConsumedMessage(traceIdHigh)).isEqualTo(traceIdString);
    }

    @Test
    void resend_originalTraceIdFound_originalTraceIdFromErrorForwarded() {
        //given
        final Error temporaryError = ErrorStubs.createTemporaryError();
        final long traceIdHigh = new Random().nextLong();
        final long traceId = new Random().nextLong();
        final long spanId = new Random().nextLong();
        final String traceIdString = "myTraceIdString";
        ReflectionTestUtils.setField(temporaryError, "originalTraceContext", OriginalTraceContext.builder()
                .traceIdHigh(traceIdHigh)
                .traceId(traceId)
                .spanId(spanId)
                .traceIdString(traceIdString)
                .build());
        kafkaTracing.messagingTracing().tracing().currentTraceContext().newScope(
                TraceContext
                        .newBuilder()
                        .spanId(new Random().nextLong())
                        .traceIdHigh(new Random().nextLong())
                        .traceId(new Random().nextLong())
                        .build());

        //when
        kafkaFailedEventResender.resend(temporaryError);

        //then
        assertThat(temporaryError.getOriginalTraceContext().getTraceIdHigh()).isEqualTo(traceIdHigh);
        assertThat(temporaryError.getOriginalTraceContext().getTraceId()).isEqualTo(traceId);
        assertThat(temporaryError.getOriginalTraceContext().getTraceIdString()).isEqualTo(traceIdString);
        assertThat(retrieveHeaderFromConsumedMessage(traceIdHigh))
                .isNotNull()
                .startsWith(HexCodec.toLowerHex(traceIdHigh))
                .endsWith(HexCodec.toLowerHex(traceId));
    }

    @Test
    void testResend_WhenEventStoredForUnknownClusterName_ThenEventWillBeSentToTheSingleConfiguredClusterAutomatically() {
        final String unknownClusterName = "unknown";
        assertThat(unknownClusterName).isNotEqualTo(kafkaProperties.getDefaultProducerClusterName());
        final Error error = ErrorStubs.createTemporaryErrorWithRandomPayload(unknownClusterName);

        kafkaFailedEventResender.resend(error);

        assertThat(hasEventBeenResent(error.getCausingEvent())).isTrue();
    }

    @AfterEach
    void cleanUp() {
        scheduledResendRepository.deleteAll();
        auditLogRepository.deleteAll();
        errorRepository.deleteAll();
        causingEventRepository.deleteAll();
    }

    private String retrieveHeaderFromConsumedMessage(long traceIdHigh) {
        return Streams.stream(consumeAllEvents())
                .filter(record -> record.headers().lastHeader("traceparent").value() != null)
                .map(record -> new String(record.headers().lastHeader("traceparent").value()))
                .filter(traceparent -> traceparent.startsWith("00-" + HexCodec.toLowerHex(traceIdHigh)))
                .findFirst().orElseThrow()
                .split("-")[1];
    }

    private boolean hasEventBeenResent(CausingEvent causingEvent) {
        return Streams.stream(consumeAllEvents()).
                map(ConsumerRecord::value).
                filter(v -> v instanceof byte[]).
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
