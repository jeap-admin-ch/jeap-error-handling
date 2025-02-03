package ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory;

import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskDto;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;

/**
 * General interface for a task factory. This will generate tasks that are then opened at the manual task service
 */
public interface TaskFactory {
    /**
     * Create a new task
     */
    TaskDto create(Error error);
}
