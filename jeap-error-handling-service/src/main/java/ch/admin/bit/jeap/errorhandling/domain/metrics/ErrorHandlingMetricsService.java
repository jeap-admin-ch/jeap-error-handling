package ch.admin.bit.jeap.errorhandling.domain.metrics;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorCountByClusterNameResult;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
// The meter registry api expects generic wildcard types for its arguments
@SuppressWarnings("java:S1452")
public class ErrorHandlingMetricsService {

    private static final String TEMP_RETRY_PENDING_GAUGE_METRIC = "eh_temporary_retry_pending";
    private static final String PERM_ERROR_OPEN_GAUGE_METRIC = "eh_permanent_open";
    private static final String PENDING_MANUALTASK_CREATE_ERRORS_GAUGE_METRIC = "eh_permanent_pending_manualtask_create";
    private static final String PENDING_MANUALTASK_RESOLVE_ERRORS_GAUGE_METRIC = "eh_permanent_pending_manualtask_resolve";
    private static final String PENDING_MANUALTASK_DELETE_ERRORS_GAUGE_METRIC = "eh_permanent_pending_manualtask_delete";
    private static final String CREATED_TEMPORARY_ERRORS_COUNTER_METRIC = "eh_created_temporary_errors";
    private static final String CREATED_PERMANENT_ERRORS_COUNTER_METRIC = "eh_created_permanent_errors";
    private static final String OPEN_ERRORS_BY_CLUSTER_GAUGE_METRIC = "eh_open_errors_by_cluster";
    public static final String CAUSING_SERVICE_TAG = "causing_service";
    public static final String CLUSTER_TAG = "cluster";
    private static final String ERROR_GROUPS_WITH_OPEN_ERRORS_GAUGE_METRIC = "eh_error_groups_with_open_errors";
    private static final String UNKNOWN_CLUSTER = "unknown";
    private static final Set<Error.ErrorState> OPEN_ERROR_STATES = Set.of(Error.ErrorState.PERMANENT, Error.ErrorState.SEND_TO_MANUALTASK);

    private final MeterRegistry meterRegistry;
    private final ErrorRepository errorRepository;
    private final ErrorGroupRepository errorGroupRepository;

    /**
     * Holds the current open error count per cluster name. The multi gauge rows read their values from these
     * counters, i.e. a row is registered once per cluster name and is never re-registered or removed afterwards.
     */
    private final Map<String, AtomicLong> openErrorCountsByClusterName = new ConcurrentHashMap<>();

    private int temporaryRetryPendingErrorCount = -1;
    private int pendingManualTaskCreationErrorCount = -1;
    private int openPermanentErrorCount = -1;
    private int resolveOnManualTaskErrorCount = -1;
    private int deleteOnManualTaskErrorCount = -1;
    private int errorGroupsWithOpenErrors = -1;
    private Counter createdTemporaryErrors;
    private MultiGauge openErrorsByClusterGauge;

    @PostConstruct
    void initialize() {
        openErrorsByClusterGauge = MultiGauge.builder(OPEN_ERRORS_BY_CLUSTER_GAUGE_METRIC)
                .description("Open errors per cluster (PERMANENT, TEMPORARY_RETRY_PENDING, or SEND_TO_MANUALTASK)")
                .register(meterRegistry);

        updateGauges();

        Gauge.builder(TEMP_RETRY_PENDING_GAUGE_METRIC, () -> temporaryRetryPendingErrorCount)
                .description("Temporary errors with pending retry")
                .register(meterRegistry);
        Gauge.builder(PERM_ERROR_OPEN_GAUGE_METRIC, () -> openPermanentErrorCount)
                .description("Open permanent errors")
                .register(meterRegistry);
        Gauge.builder(PENDING_MANUALTASK_CREATE_ERRORS_GAUGE_METRIC, () -> pendingManualTaskCreationErrorCount)
                .description("Permanent errors with pending manual task creation")
                .register(meterRegistry);
        Gauge.builder(PENDING_MANUALTASK_RESOLVE_ERRORS_GAUGE_METRIC, () -> resolveOnManualTaskErrorCount)
                .description("Permanent errors with pending manual task resolution")
                .register(meterRegistry);
        Gauge.builder(PENDING_MANUALTASK_DELETE_ERRORS_GAUGE_METRIC, () -> deleteOnManualTaskErrorCount)
                .description("Permanent errors with pending manual task deletion")
                .register(meterRegistry);
        Gauge.builder(ERROR_GROUPS_WITH_OPEN_ERRORS_GAUGE_METRIC, () -> errorGroupsWithOpenErrors)
                .description("Error groups with open errors")
                .register(meterRegistry);

        createdTemporaryErrors = Counter.builder(CREATED_TEMPORARY_ERRORS_COUNTER_METRIC)
                .description("Created temporary errors")
                .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${jeap.errorhandling.metrics.updateFrequencyMillis}")
    void updateGauges() {
        temporaryRetryPendingErrorCount = errorRepository.countErrorsInStateTemporaryRetryPending();
        openPermanentErrorCount = errorRepository.countErrorsInStatesPermanentOrSendToManualTask();
        pendingManualTaskCreationErrorCount = errorRepository.countErrorsInStateSendToManualTask();
        resolveOnManualTaskErrorCount = errorRepository.countErrorsInStateResolveOnManualTask();
        deleteOnManualTaskErrorCount = errorRepository.countErrorsInStateDeleteOnManualTask();
        errorGroupsWithOpenErrors = errorGroupRepository.countErrorGroupsWithErrorsInStates(OPEN_ERROR_STATES);
        List<ErrorCountByClusterNameResult> openErrorCountByClusterNameResults = errorRepository.countOpenErrorsByStateAndClusterName();
        updateClusterMetrics(openErrorCountByClusterNameResults);
    }

    public void incrementPermanentCounter(String causingService) {
        Counter.builder(CREATED_PERMANENT_ERRORS_COUNTER_METRIC)
                .description("Created permanent errors")
                .tag(CAUSING_SERVICE_TAG, causingService)
                .register(meterRegistry)
                .increment();
    }

    public void incrementTemporaryCounter() {
        createdTemporaryErrors.increment();
    }

    /**
     * Updates the open error count per cluster name. Clusters that no longer have any open errors are reported as
     * zero instead of being removed from the multi gauge: removing and re-registering multi gauge rows on every
     * update unregisters and re-registers the underlying prometheus collector, which makes a metrics scrape
     * running concurrently to the update fail with a duplicate labels error.
     */
    private void updateClusterMetrics(List<ErrorCountByClusterNameResult> results) {
        openErrorCountsByClusterName.values().forEach(count -> count.set(0));

        boolean clusterNameAdded = false;
        for (ErrorCountByClusterNameResult result : results) {
            String clusterName = result.clusterName() != null ? result.clusterName() : UNKNOWN_CLUSTER;
            AtomicLong count = openErrorCountsByClusterName.get(clusterName);
            if (count == null) {
                openErrorCountsByClusterName.put(clusterName, new AtomicLong(result.errorCount()));
                clusterNameAdded = true;
            } else {
                count.set(result.errorCount());
            }
        }

        if (clusterNameAdded) {
            registerClusterGaugeRows();
        }
    }

    private void registerClusterGaugeRows() {
        List<MultiGauge.Row<AtomicLong>> rows = openErrorCountsByClusterName.entrySet().stream()
                .map(entry -> MultiGauge.Row.of(Tags.of(CLUSTER_TAG, entry.getKey()), entry.getValue(), AtomicLong::doubleValue))
                .toList();

        // Do not overwrite already registered rows, they read their value from the counter map anyway
        openErrorsByClusterGauge.register(rows, false);
    }
}
