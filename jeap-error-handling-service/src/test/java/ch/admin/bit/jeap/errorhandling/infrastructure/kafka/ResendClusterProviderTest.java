package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.recordformat.RecordBinaryFormat;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMessage;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import ch.admin.bit.jeap.messaging.kafka.serde.glue.JeapGlueAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ResendClusterProviderTest {
    private KafkaProperties kafkaProperties;
    private KafkaConfiguration kafkaConfiguration;
    private ResendClusterProvider provider;

    @BeforeEach
    void setUp() {
        kafkaProperties = mock(KafkaProperties.class);
        kafkaConfiguration = mock(KafkaConfiguration.class);
        when(kafkaProperties.getDefaultProducerClusterName())
                .thenReturn("clusterA");
        when(kafkaProperties.clusterNames())
                .thenReturn(Set.of("clusterA", "clusterB"));

        when(kafkaConfiguration.producerConfig("clusterA")).thenReturn(Map.of(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CustomKafkaAvroSerializer.class.getName()));
        when(kafkaConfiguration.producerConfig("clusterB")).thenReturn(Map.of(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JeapGlueAvroSerializer.class));

        provider = new ResendClusterProvider(kafkaProperties, kafkaConfiguration);
    }

    @Test
    void testResendToSameClusterWhenFormatMatches() {
        var event = mock(CausingEvent.class);
        var message = mock(EventMessage.class);
        when(event.getMessage()).thenReturn(message);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(message.getClusterNameOrDefault("clusterA")).thenReturn("clusterA");
        when(message.getPayload()).thenReturn(new byte[]{RecordBinaryFormat.CONFLUENT_AVRO.getVersionByteValue()});

        String result = provider.getResendClusterNameFor(event);

        assertEquals("clusterA", result);
    }

    @Test
    void testResendToAlternativeClusterWhenFormatDoesNotMatch() {
        var event = mock(CausingEvent.class);
        var message = mock(EventMessage.class);
        when(event.getMessage()).thenReturn(message);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(message.getClusterNameOrDefault("clusterA")).thenReturn("clusterB");
        when(message.getPayload()).thenReturn(new byte[]{RecordBinaryFormat.CONFLUENT_AVRO.getVersionByteValue()});

        String result = provider.getResendClusterNameFor(event);

        assertEquals("clusterA", result);
    }

    @Test
    void testThrowsWhenNoSuitableClusterFound() {
        reset(kafkaProperties);
        kafkaProperties = mock(KafkaProperties.class);
        when(kafkaProperties.getDefaultProducerClusterName())
                .thenReturn("clusterA");
        when(kafkaProperties.clusterNames())
                .thenReturn(Set.of()); // empty cluster list to provoke that no suitable cluster is found
        provider = new ResendClusterProvider(kafkaProperties, kafkaConfiguration);

        var event = mock(CausingEvent.class);
        var message = mock(EventMessage.class);
        when(event.getMessage()).thenReturn(message);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(message.getClusterNameOrDefault("clusterA")).thenReturn("clusterA");
        when(message.getPayload()).thenReturn(new byte[]{RecordBinaryFormat.GLUE_AVRO.getVersionByteValue()});

        assertThatThrownBy(() -> provider.getResendClusterNameFor(event))
                .isInstanceOf(ResendFailedException.class)
                .hasMessageContaining("No suitable cluster found");
    }

    @Test
    void testThrowsWhenMessageFormatUnknown() {
        var event = mock(CausingEvent.class);
        var message = mock(EventMessage.class);
        when(event.getMessage()).thenReturn(message);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(message.getClusterNameOrDefault("clusterA")).thenReturn("unknown");
        when(message.getPayload()).thenReturn(new byte[]{42});

        assertThatThrownBy(() -> provider.getResendClusterNameFor(event))
                .isInstanceOf(ResendFailedException.class)
                .hasMessageContaining("Unknown message record binary format");
    }
}
