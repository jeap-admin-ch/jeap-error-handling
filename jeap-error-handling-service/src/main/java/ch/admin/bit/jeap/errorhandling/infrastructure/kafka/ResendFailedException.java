package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.recordformat.RecordBinaryFormat;

import java.util.UUID;

public class ResendFailedException extends RuntimeException {

    private ResendFailedException(String message) {
        super(message);
    }

    private ResendFailedException(String message, Exception e) {
        super(message, e);
    }

    public static ResendFailedException unknownSerializerForCluster(String clusterName) {
        return new ResendFailedException("Unknown serializer type / message format for cluster '" + clusterName + "'");
    }

    public static ResendFailedException unkownMessageRecordBinaryFormat(UUID causingEventId,
                                                                        String clusterNameWhereFailedEventWasConsumed) {
        return new ResendFailedException("Resend of event '" + causingEventId +
                "' failed: Unknown message record binary format, consumed from cluster '" + clusterNameWhereFailedEventWasConsumed + "'");
    }

    public static ResendFailedException noSuitableClusterFoundForMessageFormat(UUID causingEventId, RecordBinaryFormat recordBinaryFormatOfMessage) {
        return new ResendFailedException("Resend of event '" + causingEventId +
                "' failed: No suitable cluster found for message record binary format '" + recordBinaryFormatOfMessage + "'");
    }

    public static ResendFailedException resendToKafkaInterrupted(String causingEventId, UUID errorId, String topic, String clusterName) {
        return new ResendFailedException("Resend of event '%s' to Kafka for error with id %s to topic '%s' on cluster '%s' was interrupted.".
                formatted(causingEventId, errorId, topic, clusterName));
    }

    public static ResendFailedException resendToKafkaFailed(String causingEventId, UUID errorId, String topic, String clusterName, Exception e) {
        return new ResendFailedException("Resend of event '%s' to Kafka for error with id %s to topic '%s' on cluster '%s' failed: %s.".
                formatted(causingEventId, errorId, topic, clusterName, e.getMessage()), e);
    }

}
