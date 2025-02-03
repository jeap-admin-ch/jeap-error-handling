package ch.admin.bit.jeap.errorhandling.domain.manualtask.resend;

import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
@Slf4j
@RequiredArgsConstructor
class TasksSynchronize {
    private final ErrorService errorService;
    private final TasksSynchronizeProperties tasksSynchronizeProperties;

    @Scheduled(cron = "#{@tasksSynchronizeProperties.cronExpression}")
    @SchedulerLock(name = "sync-pending-tasks", lockAtLeastFor = "#{@tasksSynchronizeProperties.lockAtLeast.toString()}", lockAtMostFor = "#{@tasksSynchronizeProperties.lockAtMost.toString()}")
    public void syncWithManualTask() {
        LockAssert.assertLocked();
        syncState(Error.ErrorState.SEND_TO_MANUALTASK, errorService::createManualTask, "opened");
        syncState(Error.ErrorState.RESOLVE_ON_MANUALTASK, errorService::closeManualTask, "resolved");
        syncState(Error.ErrorState.DELETE_ON_MANUALTASK, errorService::deleteManualTask, "deleted");
    }

    private void syncState(Error.ErrorState state, Consumer<Error> handler, String action) {
        int processedChunks = 0;
        while (processedChunks < tasksSynchronizeProperties.getMaxConsecutiveChunks()) {
            log.trace("Fetching at max {} errors not yet {} at manual task service.", tasksSynchronizeProperties.getMaxResendChunkSize(), action);
            List<Error> chunk = errorService.getErrorListByState(state, 0, tasksSynchronizeProperties.getMaxResendChunkSize()).getErrors();
            log.debug("Got {} tasks not yet {}", chunk.size(), action);
            chunk.forEach(handler);
            processedChunks++;
            if (processedChunks < tasksSynchronizeProperties.getMaxResendChunkSize()) {
                log.trace("As not full, this was the last page");
                return;
            }
        }
        log.debug("Maximum number of consecutive chunks reached. Proceeding at next execution...");
    }
}
