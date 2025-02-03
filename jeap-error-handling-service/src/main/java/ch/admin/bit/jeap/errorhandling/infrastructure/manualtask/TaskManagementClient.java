package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import ch.admin.bit.jeap.security.restclient.JeapOAuth2RestClientBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

/**
 * This class implements the communication with the Agir task management service.
 */
@Component
@Slf4j
public class TaskManagementClient {

    private static final String TASKS_ENDPOINT = "api/tasks";
    private static final String TASKS_CONFIG_ENDPOINT = "api/task-configs";

    private final RestClient restClient;
    private final TaskManagementServiceProperties taskManagementServiceProperties;

    private List<TaskTypDto> taskTypesToInitialize = null;

    TaskManagementClient(JeapOAuth2RestClientBuilderFactory jeapOAuth2RestClientBuilderFactory, TaskManagementServiceProperties taskManagementServiceProperties) {
        this.taskManagementServiceProperties = taskManagementServiceProperties;
        if (this.taskManagementServiceProperties.isEnabled()) {
            ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                    .withReadTimeout(taskManagementServiceProperties.getTimeout()));
            this.restClient = jeapOAuth2RestClientBuilderFactory.createForClientRegistryId(this.taskManagementServiceProperties.getClientId())
                    .requestFactory(requestFactory)
                    .baseUrl(this.taskManagementServiceProperties.getUrl())
                    .requestInterceptor(TaskManagementClient::logRequest)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    private static ClientHttpResponse logRequest(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Task Management Request: {} {}",
                    keyValue("method", request.getMethod()),
                    keyValue("url", request.getURI()));
        }
        return execution.execute(request, body);
    }

    public synchronized void lazilyInitializeTaskTypes(List<TaskTypDto> taskTypeDtos) {
        log.info("Got task type definitions to lazily initialize at Agir: {}.", taskTypeDtos);
        taskTypesToInitialize = new ArrayList<>(taskTypeDtos);
    }

    public void createTaskTypes(List<TaskTypDto> taskTypeDtos) {
        log.debug("Creating task types {}.", taskTypeDtos);
        if (taskManagementServiceProperties.isEnabled()) {
            try {
                putTaskTypes(taskTypeDtos);
            } catch (Exception e) {
                rethrowAsTaskManagementException("Could not create task types at task management service", e);
            }
        } else {
            log.info("Task Management Service is disabled: Task types {} not created.", taskTypeDtos);
        }
    }

    public void createTask(TaskDto taskDto) {
        log.debug("Creating task {}.", taskDto);
        lazyTaskTypesInitialization();
        if (taskManagementServiceProperties.isEnabled()) {
            try {
                putTask(taskDto);
            } catch (Exception e) {
                rethrowAsTaskManagementException("Could not create task at task management service", e);
            }
        } else {
            log.info("Task Management Service is disabled: Task {} not created.", taskDto);
        }
    }

    public void closeTask(UUID taskId) {
        log.debug("Closing task with id '{}'.", taskId);
        if (taskManagementServiceProperties.isEnabled()) {
            try {
                putTaskClosed(taskId.toString());
            } catch (Exception e) {
                rethrowAsTaskManagementException("Could not close task at task management service", e);
            }
        } else {
            log.info("Task Management Service is disabled: Task with id '{}' not closed.", taskId);
        }
    }

    private synchronized void lazyTaskTypesInitialization() {
        if (taskTypesToInitialize != null) {
            log.info("Starting lazy initialization of task types {}.", taskTypesToInitialize);
            createTaskTypes(taskTypesToInitialize);
            log.info("Ended lazy initialization of task types {}.", taskTypesToInitialize);
            taskTypesToInitialize = null;
        }
    }

    private void rethrowAsTaskManagementException(String errorMessage, Exception exception) {
        if (exception instanceof TaskManagementException taskManagementException) {
            // Do not wrap a TaskManagementException into another TaskManagementException
            throw taskManagementException;
        } else {
            throw new TaskManagementException(errorMessage, exception);
        }
    }

    private void putTask(TaskDto taskDto) {
        log.debug("Trying to create or update the task {}", taskDto);
        try {
            restClient.put().uri(TASKS_ENDPOINT + "/{id}", taskDto.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(taskDto)
                .retrieve()
                .toBodilessEntity();
            log.info("Successfully created or updated the task {}.", taskDto);
        } catch (Exception e) {
            log.error("Failed to create or update the task {} because of an exception:", taskDto, e);
            throw e;
        }
    }

    private void putTaskClosed(String taskId) {
        log.debug("Trying to close the task with id '{}'", taskId);
        TaskStateDto taskStateDto = new TaskStateDto(TaskStatus.CLOSED);
        try {
            restClient.put().uri(TASKS_ENDPOINT + "/{id}/state", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(taskStateDto)
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                    // Ignore when attempting to close a task that no longer exists
                    log.warn("Task with id '{}' could not be closed as it no longer exists", taskId);
                })
                .toBodilessEntity();
            log.info("Successfully closed the task with id '{}'.", taskId);
        } catch (Exception e) {
            log.error("Failed to close the task with id '{}' because of an exception:", taskId, e);
            throw e;
        }
    }

    private void putTaskTypes(List<TaskTypDto> taskTypes) {
        log.debug("Trying to create or update the task types {}.", taskTypes);
        try {
            restClient.put().uri(TASKS_CONFIG_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(taskTypes)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully created or updated the task types {}.", taskTypes);
        } catch (Exception e) {
            log.warn("Failed to create of update the task types {} because of an exception:", taskTypes, e);
            throw e;
        }
    }

}
