package ch.admin.bit.jeap.errorhandling.domain.metrics;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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
    public static final String CAUSING_SERVICE_TAG = "causing_service";

    private final MeterRegistry meterRegistry;
    private final ErrorRepository errorRepository;

    private int temporaryRetryPendingErrorCount = -1;
    private int pendingManualTaskCreationErrorCount = -1;
    private int openPermanentErrorCount = -1;
    private int resolveOnManualTaskErrorCount = -1;
    private int deleteOnManualTaskErrorCount = -1;
    private Counter createdTemporaryErrors;

    @PostConstruct
    void initialize() {
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
}
