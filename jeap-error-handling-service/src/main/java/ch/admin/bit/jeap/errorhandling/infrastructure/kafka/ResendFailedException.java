package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.recordformat.RecordBinaryFormat;

import java.util.UUID;

public class ResendFailedException extends RuntimeException {

    private ResendFailedException(String message) {
        super(message);
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
}
