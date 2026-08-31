package ch.admin.bit.jeap.errorhandling.domain.manualtask.resend;

import ch.admin.bit.jeap.errorhandling.domain.error.ErrorList;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TasksSynchronizeTest {

    @Mock
    private ErrorService errorService;

    private TasksSynchronizeProperties properties;
    private TasksSynchronize tasksSynchronize;

    @BeforeEach
    void setUp() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        properties = new TasksSynchronizeProperties();
        properties.setMaxResendChunkSize(2);
        properties.setMaxConsecutiveChunks(3);
        tasksSynchronize = new TasksSynchronize(errorService, properties);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void processesFurtherChunksUntilAChunkIsNotFull() {
        Error first = mock(Error.class);
        Error second = mock(Error.class);
        Error third = mock(Error.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        when(first.getId()).thenReturn(firstId);
        when(second.getId()).thenReturn(secondId);
        when(third.getId()).thenReturn(thirdId);
        when(errorService.getErrorListByStateExcluding(Error.ErrorState.SEND_TO_MANUALTASK, Set.of(), 2))
                .thenReturn(new ErrorList(0, List.of()));
        when(errorService.getErrorListByStateExcluding(Error.ErrorState.RESOLVE_ON_MANUALTASK, Set.of(), 2))
                .thenReturn(new ErrorList(3, List.of(first, second)));
        when(errorService.getErrorListByStateExcluding(
                Error.ErrorState.RESOLVE_ON_MANUALTASK, Set.of(firstId, secondId), 2))
                .thenReturn(new ErrorList(1, List.of(third)));
        when(errorService.getErrorListByStateExcluding(Error.ErrorState.DELETE_ON_MANUALTASK, Set.of(), 2))
                .thenReturn(new ErrorList(0, List.of()));

        tasksSynchronize.syncWithManualTask();

        verify(errorService, times(2))
                .getErrorListByStateExcluding(
                        org.mockito.ArgumentMatchers.eq(Error.ErrorState.RESOLVE_ON_MANUALTASK),
                        org.mockito.ArgumentMatchers.anySet(),
                        org.mockito.ArgumentMatchers.eq(2));
        verify(errorService).closeManualTask(first);
        verify(errorService).closeManualTask(second);
        verify(errorService).closeManualTask(third);
    }
}
