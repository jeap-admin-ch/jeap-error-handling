package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import brave.kafka.clients.KafkaTracing;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.MessageHeader;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContext;
import ch.admin.bit.jeap.messaging.kafka.tracing.TraceContextUpdater;
import ch.admin.bit.jeap.messaging.kafka.tracing.TracingKafkaProducerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;

@Component
@Slf4j
public class KafkaFailedEventResender {

    private static final String TARGET_SERVICE_HEADER_NAME = "jeap_eh_target_service";
    private static final String ERROR_HANDLING_SERVICE_HEADER_NAME = "jeap_eh_error_handling_service";

    private final ResendClusterProvider resendClusterProvider;
    private final TraceContextUpdater traceContextUpdater;
    private final int timeoutSeconds;
    private final String errorHandlerServiceName;

    private final Map<String, KafkaTemplate<Object, Object>> kafkaTemplateByClusterName;

    public KafkaFailedEventResender(ResendClusterProvider resendClusterProvider,
                                    KafkaProperties kafkaProperties,
                                    KafkaConfiguration kafkaConfiguration,
                                    KafkaTracing kafkaTracing,
                                    TraceContextUpdater traceContextUpdater,
                                    @Value("${jeap.errorhandling.timeout-seconds:60}") int timeoutSeconds) {
        this.resendClusterProvider = resendClusterProvider;
        this.traceContextUpdater = traceContextUpdater;
        this.errorHandlerServiceName = kafkaProperties.getServiceName();
        this.timeoutSeconds = timeoutSeconds;
        this.kafkaTemplateByClusterName = kafkaProperties.clusterNames().stream()
                .collect(toMap(clusterName -> clusterName,
                        clusterName -> createKafkaTemplate(kafkaConfiguration, kafkaTracing, clusterName)));
    }

    private static KafkaTemplate<Object, Object> createKafkaTemplate(KafkaConfiguration kafkaConfiguration, KafkaTracing kafkaTracing, String clusterName) {
        return new KafkaTemplate<>(new TracingKafkaProducerFactory<>(kafkaTracing.messagingTracing(), adaptKafkaConfiguration(clusterName, kafkaConfiguration)));
    }

    public void resend(final Error error) {
        final byte[] message = error.getCausingEventMessage().getPayload();
        final byte[] key = error.getCausingEventMessage().getKey();
        final String topic = error.getCausingEventMessage().getTopic();
        final String clusterName = resendClusterProvider.getResendClusterNameFor(error.getCausingEvent());
        final CompletableFuture<SendResult<Object, Object>> sendResult;
        log.info("Resending event {} for error {} to topic '{}' on cluster '{}'.",
                error.getCausingEventMetadata().getId(), error.getId(), topic, clusterName);

        if (error.getOriginalTraceContext() != null) {
            log.debug("Original traceId found on the error to resend. Overriding the current tracing context with the original traceId {}", error.getOriginalTraceContext());
            traceContextUpdater.setTraceContext(new TraceContext(
                    error.getOriginalTraceContext().getTraceIdHigh(),
                    error.getOriginalTraceContext().getTraceId(),
                    error.getOriginalTraceContext().getSpanId(),
                    error.getOriginalTraceContext().getParentSpanId(),
                    error.getOriginalTraceContext().getTraceIdString()));
        }

        ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(topic, key, message);
        addHeadersFromCausingEvent(error, producerRecord);
        addResendInformationHeaders(error, producerRecord);
        sendResult = kafkaTemplateByClusterName.get(clusterName).send(producerRecord);

        try {
            sendResult.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Cannot resend event on Kafka", e);
        }
        log.info("Resent event {} for error {} to topic '{}' on cluster '{}'.",
                error.getCausingEventMetadata().getId(), error.getId(), topic, clusterName);
    }

    private static void addHeadersFromCausingEvent(Error error, ProducerRecord<Object, Object> producerRecord) {
        List<MessageHeader> headers = error.getCausingEvent().getHeaders();
        if (headers != null) {
            for (MessageHeader header : headers) {
                producerRecord.headers().add(header.getHeaderName(), header.getHeaderValue());
            }
        }
    }

    private void addResendInformationHeaders(Error error, ProducerRecord<Object, Object> producerRecord) {
        // Only headers from encryption and signing should be there, but we remove them anyway to be sure
        producerRecord.headers().remove(TARGET_SERVICE_HEADER_NAME);
        producerRecord.headers().remove(ERROR_HANDLING_SERVICE_HEADER_NAME);

        addHeader(producerRecord, TARGET_SERVICE_HEADER_NAME, error.getCausingEvent().getMetadata().getPublisher().getService());
        addHeader(producerRecord, ERROR_HANDLING_SERVICE_HEADER_NAME, errorHandlerServiceName);
    }

    private static void addHeader(ProducerRecord<Object, Object> producerRecord, String headerName, String headerValue) {
        producerRecord.headers().add(headerName, headerValue.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> adaptKafkaConfiguration(String clusterName, KafkaConfiguration kafkaConfiguration) {
        Map<String, Object> props = new HashMap<>(kafkaConfiguration.producerConfig(clusterName));
        // We are resending messages exactly as received i.e. as byte array of the original message
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        // Remove the interceptor configured by the domain event library, because it expects messages to be domain events.
        props.remove(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return props;
    }
}
