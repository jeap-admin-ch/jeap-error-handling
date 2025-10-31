package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

public record ErrorCountByClusterNameResult(String clusterName, Long errorCount) {
}
