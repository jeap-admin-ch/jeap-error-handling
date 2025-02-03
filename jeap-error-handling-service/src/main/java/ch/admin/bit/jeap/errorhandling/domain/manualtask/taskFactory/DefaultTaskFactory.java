package ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory;

import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.DefaultTaskFactoryProperties.TaskDisplayProperties;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import lombok.RequiredArgsConstructor;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class DefaultTaskFactory implements TaskFactory {

    private final static String TYPE = "errorhandling";

    private final DefaultTaskFactoryProperties taskFactoryProperties;
    private final TaskManagementServiceProperties taskManagementServiceProperties;
    private final TaskManagementClient taskManagementClient;

    @PostConstruct
    public void createTaskTypes() throws TaskManagementException {
        if (!taskManagementServiceProperties.isEnabled()) {
            return;
        }
        taskFactoryProperties.validate();

        //If other languages are needed here add them as well
        List<TaskTypeDisplayDto> taskTypeDisplayDtos = taskFactoryProperties.getDisplay().entrySet().stream()
                .map(entry -> toTaskTypeDisplayDto(entry.getKey(), entry.getValue()))
                .collect(toList());

        TaskTypDto errorType = TaskTypDto.builder()
                .name(TYPE)
                .system(taskFactoryProperties.getSystem())
                .display(taskTypeDisplayDtos)
                .domain(taskFactoryProperties.getDomain())
                .build();
        // If we want to initialize the task types at Agir in a @PostConstruct method we must do so lazily because
        // at this moment the spring context will not yet be fully initialized and WebClient calls will not yet work.
        taskManagementClient.lazilyInitializeTaskTypes(singletonList(errorType));
    }

    private static TaskTypeDisplayDto toTaskTypeDisplayDto(String language, TaskDisplayProperties displayProperties) {
        return TaskTypeDisplayDto.builder()
                .language(language.toUpperCase())
                .title(displayProperties.getTitle())
                .description(displayProperties.getDescription())
                .displayName(displayProperties.getDisplayName())
                .displayDomain(displayProperties.getDisplayDomain())
                .build();
    }

    @Override
    public TaskDto create(Error error) {
        TaskReferenceDto errorServiceReference = TaskReferenceDto.builder()
                .name(taskFactoryProperties.getTaskReferenceName())
                .uri(taskFactoryProperties.getErrorServiceBaseUrl() + error.getId())
                .build();
        return TaskDto.builder()
                .id(UUID.randomUUID())
                .type(TYPE)
                .system(taskFactoryProperties.getSystem())
                .service(error.getCausingEventMetadata().getPublisher().getService())
                .state(TaskStatus.OPEN)
                .priority(taskFactoryProperties.getPriority())
                .due(LocalDateTime.now().plus(taskFactoryProperties.getTimeToHandle()))
                .reference(errorServiceReference)
                .build();
    }

}
