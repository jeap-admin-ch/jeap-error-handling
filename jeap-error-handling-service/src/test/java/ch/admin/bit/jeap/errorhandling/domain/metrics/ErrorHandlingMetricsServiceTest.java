package ch.admin.bit.jeap.errorhandling.domain.metrics;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorGroupRepository;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error.ErrorState;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorHandlingMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;

    @Mock
    private ErrorRepository errorRepository;
    @Mock
    private ErrorGroupRepository errorGroupRepository;

    private ErrorHandlingMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new ErrorHandlingMetricsService(meterRegistry, errorRepository, errorGroupRepository);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void initializeRegistersGaugesWithCountsFromRepositories() {
        when(errorRepository.countErrorsInStateTemporaryRetryPending()).thenReturn(3);
        when(errorRepository.countErrorsInStatesPermanentOrSendToManualTask()).thenReturn(7);
        when(errorRepository.countErrorsInStateSendToManualTask()).thenReturn(4);
        when(errorRepository.countErrorsInStateResolveOnManualTask()).thenReturn(2);
        when(errorRepository.countErrorsInStateDeleteOnManualTask()).thenReturn(1);
        when(errorGroupRepository.countErrorGroupsWithErrorsInStates(any())).thenReturn(5);

        metricsService.initialize();

        Assertions.assertThat(meterRegistry.get("eh_temporary_retry_pending").gauge().value()).isEqualTo(3);
        Assertions.assertThat(meterRegistry.get("eh_permanent_open").gauge().value()).isEqualTo(7);
        Assertions.assertThat(meterRegistry.get("eh_permanent_pending_manualtask_create").gauge().value()).isEqualTo(4);
        Assertions.assertThat(meterRegistry.get("eh_permanent_pending_manualtask_resolve").gauge().value()).isEqualTo(2);
        Assertions.assertThat(meterRegistry.get("eh_permanent_pending_manualtask_delete").gauge().value()).isEqualTo(1);
        Assertions.assertThat(meterRegistry.get("eh_error_groups_with_open_errors").gauge().value()).isEqualTo(5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<ErrorState>> statesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(errorGroupRepository).countErrorGroupsWithErrorsInStates(statesCaptor.capture());
        Assertions.assertThat(statesCaptor.getValue())
                .containsExactlyInAnyOrder(ErrorState.PERMANENT, ErrorState.SEND_TO_MANUALTASK);
    }

    @Test
    void updateGaugesRefreshesValues() {
        when(errorRepository.countErrorsInStateTemporaryRetryPending()).thenReturn(1, 6);
        when(errorRepository.countErrorsInStatesPermanentOrSendToManualTask()).thenReturn(2, 8);
        when(errorRepository.countErrorsInStateSendToManualTask()).thenReturn(3, 4);
        when(errorRepository.countErrorsInStateResolveOnManualTask()).thenReturn(4, 1);
        when(errorRepository.countErrorsInStateDeleteOnManualTask()).thenReturn(5, 0);
        when(errorGroupRepository.countErrorGroupsWithErrorsInStates(any())).thenReturn(2, 7);
        metricsService.initialize();

        metricsService.updateGauges();

        Assertions.assertThat(meterRegistry.get("eh_temporary_retry_pending").gauge().value()).isEqualTo(6);
        Assertions.assertThat(meterRegistry.get("eh_permanent_open").gauge().value()).isEqualTo(8);
        Assertions.assertThat(meterRegistry.get("eh_permanent_pending_manualtask_create").gauge().value()).isEqualTo(4);
        Assertions.assertThat(meterRegistry.get("eh_permanent_pending_manualtask_resolve").gauge().value()).isEqualTo(1);
        Assertions.assertThat(meterRegistry.get("eh_permanent_pending_manualtask_delete").gauge().value()).isZero();
        Assertions.assertThat(meterRegistry.get("eh_error_groups_with_open_errors").gauge().value()).isEqualTo(7);
    }

    @Test
    void incrementTemporaryCounterIncrementsMetric() {
        metricsService.initialize();

        metricsService.incrementTemporaryCounter();
        metricsService.incrementTemporaryCounter();

        Assertions.assertThat(meterRegistry.get("eh_created_temporary_errors").counter().count()).isEqualTo(2);
    }

    @Test
    void incrementPermanentCounterCreatesTaggedCounters() {
        metricsService.initialize();

        metricsService.incrementPermanentCounter("service-a");
        metricsService.incrementPermanentCounter("service-a");
        metricsService.incrementPermanentCounter("service-b");

        Assertions.assertThat(meterRegistry.get("eh_created_permanent_errors").tags("causing_service", "service-a")
                .counter().count()).isEqualTo(2);
        Assertions.assertThat(meterRegistry.get("eh_created_permanent_errors").tags("causing_service", "service-b")
                .counter().count()).isEqualTo(1);
    }
}
