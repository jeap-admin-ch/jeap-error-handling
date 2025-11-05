package ch.admin.bit.jeap.errorhandling.infrastructure.kafka.recordformat;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.ResendFailedException;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.serde.confluent.CustomKafkaAvroSerializer;
import ch.admin.bit.jeap.messaging.kafka.serde.glue.JeapGlueAvroSerializer;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Optional;

public enum RecordBinaryFormat {
    /**
     * See {@link io.confluent.kafka.serializers.AbstractKafkaSchemaSerDe#MAGIC_BYTE}
     */
    CONFLUENT_AVRO((byte) 0),
    /**
     * The version byte value for AWS Glue is '3'
     */
    GLUE_AVRO(AWSSchemaRegistryConstants.HEADER_VERSION_BYTE);

    private final byte versionByteValue;

    RecordBinaryFormat(byte versionByteValue) {
        this.versionByteValue = versionByteValue;
    }

    public byte getVersionByteValue() {
        return versionByteValue;
    }

    public static Optional<RecordBinaryFormat> of(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return Optional.empty();

        // Select format by first byte
        for (RecordBinaryFormat recordBinaryFormat : values()) {
            if (bytes[0] == recordBinaryFormat.versionByteValue) {
                return Optional.of(recordBinaryFormat);
            }
        }

        return Optional.empty();
    }

    public static RecordBinaryFormat requireFormatForCluster(KafkaConfiguration kafkaConfiguration, String clusterName) {
        Object serializerConfig = kafkaConfiguration.producerConfig(clusterName)
                .get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        if (serializerConfig == null) {
            throw ResendFailedException.unknownSerializerForCluster(clusterName);
        }

        // jEAP Messaging sets serializer class either as Class or as String, so we need to check both
        if (JeapGlueAvroSerializer.class.getName().equals(serializerConfig) || JeapGlueAvroSerializer.class.equals(serializerConfig)) {
            return GLUE_AVRO;
        } else if (CustomKafkaAvroSerializer.class.getName().equals(serializerConfig) || CustomKafkaAvroSerializer.class.equals(serializerConfig)) {
            return CONFLUENT_AVRO;
        }

        throw ResendFailedException.unknownSerializerForCluster(clusterName);
    }
}
