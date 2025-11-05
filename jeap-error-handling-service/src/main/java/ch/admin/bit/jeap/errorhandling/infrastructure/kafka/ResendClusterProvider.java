package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.recordformat.RecordBinaryFormat;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ResendClusterProvider {
    private final String defaultProducerClusterName;
    private final Set<String> allClusterNames;
    private final Map<String, RecordBinaryFormat> recordBinaryFormatByClusterName;

    public ResendClusterProvider(KafkaProperties kafkaProperties, KafkaConfiguration kafkaConfiguration) {
        this.defaultProducerClusterName = kafkaProperties.getDefaultProducerClusterName();
        this.allClusterNames = kafkaProperties.clusterNames();

        recordBinaryFormatByClusterName = allClusterNames.stream()
                .map(clusterName -> Map.entry(clusterName, RecordBinaryFormat.requireFormatForCluster(kafkaConfiguration, clusterName)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        log.info("Record binary formats by cluster: {}", recordBinaryFormatByClusterName);
    }

    public String getResendClusterNameFor(CausingEvent causingEvent) {
        String clusterNameWhereFailedEventWasConsumed = causingEvent.getMessage().getClusterNameOrDefault(defaultProducerClusterName);
        Optional<RecordBinaryFormat> recordBinaryFormatOfMessageOpt = RecordBinaryFormat.of(causingEvent.getMessage().getPayload());
        Optional<RecordBinaryFormat> recordBinaryFormatOfOriginatingCluster = findClusterRecordBinaryFormatIfKnown(clusterNameWhereFailedEventWasConsumed);

        // Can the message be resent to the same cluster as it was originally consumed from? This is the case if the
        // originating cluster is known and the format matches. If the format is unknown, but the cluster name is valid,
        // we also resend to the originating cluster (format should never be unknown).
        if (messageFormatUnknownButClusterNameValid(recordBinaryFormatOfMessageOpt, clusterNameWhereFailedEventWasConsumed) ||
                recordBinaryFormatMatches(recordBinaryFormatOfOriginatingCluster, recordBinaryFormatOfMessageOpt)) {
            return clusterNameWhereFailedEventWasConsumed;
        }

        // Unknown message format, invalid originating cluster - cannot determine resend cluster
        if (recordBinaryFormatOfMessageOpt.isEmpty()) {
            throw ResendFailedException.unkownMessageRecordBinaryFormat(causingEvent.getId(), clusterNameWhereFailedEventWasConsumed);
        }

        // If the format does not match or the originating cluster is unknown, try to find a cluster that can handle the
        // format of the message.
        RecordBinaryFormat recordBinaryFormatOfMessage = recordBinaryFormatOfMessageOpt.get();
        for (String clusterName : allClusterNames) {
            RecordBinaryFormat recordBinaryFormatOfAlternativeCluster = getClusterRecordBinaryFormat(clusterName);
            if (recordBinaryFormatOfMessage == recordBinaryFormatOfAlternativeCluster) {
                log.info("Using alternative cluster '{}' for event '{}' because record format '{}' does not match with original cluster '{}' which is using '{}'",
                        clusterName, causingEvent.getId(), recordBinaryFormatOfMessage, clusterNameWhereFailedEventWasConsumed,
                        recordBinaryFormatOfOriginatingCluster.map(RecordBinaryFormat::name).orElse("<unknown>"));
                return clusterName;
            }
        }

        throw ResendFailedException.noSuitableClusterFoundForMessageFormat(causingEvent.getId(), recordBinaryFormatOfMessage);
    }

    private boolean messageFormatUnknownButClusterNameValid(Optional<RecordBinaryFormat> recordBinaryFormatOfMessageOpt,
                                                            String clusterNameWhereFailedEventWasConsumed) {
        return recordBinaryFormatOfMessageOpt.isEmpty() && allClusterNames.contains(clusterNameWhereFailedEventWasConsumed);
    }

    private boolean recordBinaryFormatMatches(Optional<RecordBinaryFormat> recordBinaryFormatOfOriginatingCluster,
                                              Optional<RecordBinaryFormat> recordBinaryFormatOfMessage) {
        return recordBinaryFormatOfOriginatingCluster
                .filter(recordBinaryFormat -> recordBinaryFormat == recordBinaryFormatOfMessage.orElse(null))
                .isPresent();
    }

    /**
     * If the persisted cluster name is no longer a valid cluster name in the configuration, the first cluster
     * able to process the binary format is chosen instead. This works in migration scenarios, where two clusters with
     * different record binary formats are used, and topic mirroring with target-cluster-aware re-serialization is active.
     */
    private Optional<RecordBinaryFormat> findClusterRecordBinaryFormatIfKnown(String persistedClusterName) {
        return recordBinaryFormatByClusterName.containsKey(persistedClusterName) ?
                Optional.of(getClusterRecordBinaryFormat(persistedClusterName)) : Optional.empty();
    }

    private RecordBinaryFormat getClusterRecordBinaryFormat(String clusterName) {
        return recordBinaryFormatByClusterName.get(clusterName);
    }
}
