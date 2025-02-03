package ch.admin.bit.jeap.errorhandling.infrastructure.manualtask;

import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.test.client.MockJeapOAuth2RestClientBuilderFactory;
import ch.admin.bit.jeap.security.test.client.configuration.JeapOAuth2IntegrationTestClientConfiguration;
import ch.admin.bit.jeap.security.test.jws.JwsBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@RestClientTest(value = {TaskManagementClient.class, TaskManagementServiceProperties.class},
                properties = { "jeap.errorhandling.task-management.service.enabled=true",
                                "jeap.errorhandling.task-management.service.url=http://localhost:8888",
                                "logging.level.au.com.dius.pact=DEBUG"})
@Import(JeapOAuth2IntegrationTestClientConfiguration.class)
@PactConsumerTest
@PactTestFor(pactVersion = PactSpecVersion.V3)
@MockServerConfig(hostInterface = "localhost", port = "8888")
class TaskManagementClientTest {

    // task management api constants
    private static final String TASK_API_PATH = "/api/tasks";
    private static final String TASK_CONFIG_API_PATH = "/api/task-configs";
    private static final String TASK_STATE_OPEN = "OPEN";
    private static final String TASK_STATE_CLOSED = "CLOSED";
    private static final String TASK_MANAGEMENT_SYSTEM_NAME = "agir";

    // error handling constants
    private static final String ERROR_HANDLING_SYSTEM_NAME = "jeap";
    private static final String ERROR_HANDLING_DOMAIN_NAME = "error-handling";
    private static final String ERROR_HANDLING_TASKTYPE_NAME = "errorhandling";
    private static final String ERROR_HANDLING_SERVICE_NAME = "jeap-error-handling-service";

    // provider state parameter names
    private static final String SYSTEM_PARAMETER_NAME = "system";
    private static final String TASK_TYPE_NAME_PARAMETER_NAME = "task-type-name";
    private static final String TASK_ID_PARAMETER_NAME = "id";

    // Task example valuse
    private static final String EXAMPLE_TASK_ID =  "748fda55-7411-44ba-b8f1-f84f8fc5d50e";
    private static final String EXAMPLE_TASK_PRIORITY =  "HIGH";
    private static final Instant EXAMPLE_TASK_DUE = Instant.parse("2020-04-23T16:15:10.883Z");
    private static final String EXAMPLE_TASK_REFERENCE_NAME =  "Error Handling Service";
    private static final String EXAMPLE_TASK_REFERENCE_URI = "http://localhost/test";
    private static final String EXAMPLE_TASK_ADDITIONAL_INFO_NAME = "Additional information";
    private static final String EXAMPLE_TASK_ADDITIONAL_INFO_VALUE = "Here's some additional information";

    // Task type example values
    private static final String EXAMPLE_LANGUAGE = "DE";
    private static final String EXAMPLE_TITLE = "Fehlgeschlagene Event-Verarbeitung";
    private static final String EXAMPLE_DESCRIPTION = "Ein technischer Fehler ist aufgrund eines unverarbeitbaren Events aufgetreten.";
    private static final String EXAMPLE_NAME_DISPLAY = "Eventverarbeitungsfehler";
    private static final String EXAMPLE_DOMAIN_DISPLAY = "Error Handling";
    public static final String PROVIDER = "bazg-agir-task-scs";
    public static final String CONSUMER = "bit-" + ERROR_HANDLING_SERVICE_NAME;

    // Task management roles needed to create and update tasks as well as to create/update task types.
    private static final SemanticApplicationRole CREATE_ROLE =  SemanticApplicationRole.builder()
            .system(TASK_MANAGEMENT_SYSTEM_NAME).tenant(ERROR_HANDLING_SYSTEM_NAME).resource(ERROR_HANDLING_TASKTYPE_NAME).operation("create").build();
    private static final SemanticApplicationRole UPDATE_ROLE =  SemanticApplicationRole.builder()
            .system(TASK_MANAGEMENT_SYSTEM_NAME).tenant(ERROR_HANDLING_SYSTEM_NAME).resource(ERROR_HANDLING_TASKTYPE_NAME).operation("update").build();

    @Autowired
    private TaskManagementClient target;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private MockJeapOAuth2RestClientBuilderFactory mockRestClientBuilderFactory;
    private String agirCreateRoleBearerToken;
    private String agirUpdateRoleBearerToken;
    private String agirCreateAndUpdateRoleBearerToken;
    private String nonsenseRoleBearerToken;

    @BeforeEach
    void init(@Autowired JwsBuilderFactory jwsBuilderFactory) {
        agirCreateRoleBearerToken = jwsBuilderFactory.createValidForFixedLongPeriodBuilder("error-handling-service", JeapAuthenticationContext.SYS).
            withUserRoles(CREATE_ROLE)
            .build().serialize();
        agirUpdateRoleBearerToken = jwsBuilderFactory.createValidForFixedLongPeriodBuilder("error-handling-service", JeapAuthenticationContext.SYS).
            withUserRoles(UPDATE_ROLE)
            .build().serialize();
        agirCreateAndUpdateRoleBearerToken = jwsBuilderFactory.createValidForFixedLongPeriodBuilder("error-handling-service", JeapAuthenticationContext.SYS).
            withUserRoles(CREATE_ROLE, UPDATE_ROLE)
            .build().serialize();
        nonsenseRoleBearerToken = jwsBuilderFactory.createValidForFixedLongPeriodBuilder("error-handling-service", JeapAuthenticationContext.SYS).
            withUserRoles("some undefined nonsense role in respect to Agir").
            build().serialize();
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    private RequestResponsePact createInteractionNewTaskOfExistingTaskType(PactDslWithProvider builder) {
        // @formatter:off
        return builder
                .given("a task type with name ${task-type-name} exists for system ${system}",
                        SYSTEM_PARAMETER_NAME, ERROR_HANDLING_SYSTEM_NAME,
                        TASK_TYPE_NAME_PARAMETER_NAME, ERROR_HANDLING_TASKTYPE_NAME)
                .uponReceiving("a request to create a new task of an existing task type")
                .path(TASK_API_PATH + "/" + EXAMPLE_TASK_ID)
                .method("PUT")
                .headers(HttpHeaders.AUTHORIZATION, "Bearer " + agirCreateRoleBearerToken)
                .matchHeader("Content-Type", "application/json", "application/json")
                .body(taskDtoBody(TASK_STATE_OPEN))
                .willRespondWith()
                .status(200)
                .toPact();
        // @formatter:on
    }

    @Test
    @PactTestFor(pactMethod = "createInteractionNewTaskOfExistingTaskType")
    void testInteractionCreateNewTaskOfExistingTaskType() {
        mockRestClientBuilderFactory.getAuthTokenProvider().setAuthToken(agirCreateRoleBearerToken);
        TaskDto taskDto = taskDto();
        assertDoesNotThrow(() -> target.createTask(taskDto));
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    private RequestResponsePact createInteractionCloseExistingTask(PactDslWithProvider builder) {
        // @formatter:off
        return builder
                .given("a task with id ${id} exists for system ${system} and has task type named ${task-type-name}",
                        TASK_ID_PARAMETER_NAME, EXAMPLE_TASK_ID,
                        SYSTEM_PARAMETER_NAME, ERROR_HANDLING_SYSTEM_NAME,
                        TASK_TYPE_NAME_PARAMETER_NAME, ERROR_HANDLING_TASKTYPE_NAME)
                .uponReceiving("a request to set the state of the task with id ${id} to " + TASK_STATE_CLOSED)
                .path(TASK_API_PATH + "/" + EXAMPLE_TASK_ID + "/state")
                .body(taskStateDtoBody(TASK_STATE_CLOSED))
                .method("PUT")
                .headers(HttpHeaders.AUTHORIZATION, "Bearer " + agirUpdateRoleBearerToken)
                .matchHeader("Content-Type", "application/json", "application/json")
                .willRespondWith()
                .status(200)
                .toPact();
        // @formatter:on
    }

    @Test
    @PactTestFor(pactMethod = "createInteractionCloseExistingTask")
    void testInteractionCloseExistingTask() {
        mockRestClientBuilderFactory.getAuthTokenProvider().setAuthToken(agirUpdateRoleBearerToken);
        assertDoesNotThrow(() -> target.closeTask(UUID.fromString(EXAMPLE_TASK_ID)));
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    private RequestResponsePact createInteractionCloseNonExistingTask(PactDslWithProvider builder) {
        // @formatter:off
        return builder
                .given("no task with id ${id} exists", TASK_ID_PARAMETER_NAME, EXAMPLE_TASK_ID)
                .uponReceiving("a request to set the state of the non existing task with id ${id} to " + TASK_STATE_CLOSED)
                .path(TASK_API_PATH + "/" + EXAMPLE_TASK_ID + "/state")
                .body(taskStateDtoBody(TASK_STATE_CLOSED))
                .method("PUT")
                .headers(HttpHeaders.AUTHORIZATION, "Bearer " + agirUpdateRoleBearerToken)
                .matchHeader("Content-Type", "application/json", "application/json")
                .willRespondWith()
                .status(404)
                .toPact();
        // @formatter:on
    }

    @Test
    @PactTestFor(pactMethod = "createInteractionCloseNonExistingTask")
    void testInteractionCloseNonExistingTask()  {
        mockRestClientBuilderFactory.getAuthTokenProvider().setAuthToken(agirUpdateRoleBearerToken);
        assertDoesNotThrow(() -> target.closeTask(UUID.fromString(EXAMPLE_TASK_ID)));
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    private RequestResponsePact createInteractionNewTaskType(PactDslWithProvider builder) {
        // @formatter:off
        return builder
                .given("normal operation")
                .uponReceiving("a request to create or update a task type")
                .path(TASK_CONFIG_API_PATH)
                .method("PUT")
                .headers(HttpHeaders.AUTHORIZATION, "Bearer " + agirCreateAndUpdateRoleBearerToken)
                .matchHeader("Content-Type", "application/json", "application/json")
                .body(taskTypeDtoArrayBody())
                .willRespondWith()
                .status(200)
                .toPact();
        // @formatter:on
    }

    @Test
    @PactTestFor(pactMethod = "createInteractionNewTaskType")
    void testInteractionCreateNewTaskType() throws TaskManagementException {
        mockRestClientBuilderFactory.getAuthTokenProvider().setAuthToken(agirCreateAndUpdateRoleBearerToken);
        TaskTypDto taskTypeDto = taskTypeDto();
        assertDoesNotThrow(() -> target.createTaskTypes(singletonList(taskTypeDto)));
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    private RequestResponsePact createInteractionNewTaskTypeForbidden(PactDslWithProvider builder) {
        mockRestClientBuilderFactory.getAuthTokenProvider().setAuthToken(nonsenseRoleBearerToken);
        // @formatter:off
        return builder
                .given("normal operation")
                .uponReceiving("a request to create or update a task type without the needed authorization.")
                .path(TASK_CONFIG_API_PATH)
                .method("PUT")
                .headers(HttpHeaders.AUTHORIZATION, "Bearer " + nonsenseRoleBearerToken)
                .matchHeader("Content-Type", "application/json", "application/json")
                .body(taskTypeDtoArrayBody())
                .willRespondWith()
                .status(403)
                .toPact();
        // @formatter:on
    }

    @Test
    @PactTestFor(pactMethod = "createInteractionNewTaskTypeForbidden")
    void testInteractionCreateNewTaskTypeForbidden() throws TaskManagementException {
        assertThatThrownBy(() -> {
            mockRestClientBuilderFactory.getAuthTokenProvider().setAuthToken(nonsenseRoleBearerToken);
            TaskTypDto taskTypeDto = taskTypeDto();
            target.createTaskTypes(singletonList(taskTypeDto));
        }).hasCauseInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    private PactDslJsonArray taskTypeDtoArrayBody() {
        // @formatter:off
        return new PactDslJsonArray().
                object().
                    stringType("name", ERROR_HANDLING_TASKTYPE_NAME).
                    stringType("system", ERROR_HANDLING_SYSTEM_NAME).
                    stringType("domain", ERROR_HANDLING_DOMAIN_NAME).
                    eachLike("display").
                        stringType("language", EXAMPLE_LANGUAGE ).
                        stringType("title", EXAMPLE_TITLE).
                        stringType("description", EXAMPLE_DESCRIPTION).
                        stringType("displayName", EXAMPLE_NAME_DISPLAY).
                        stringType("displayDomain", EXAMPLE_DOMAIN_DISPLAY).
                        close().
                asArray();
        // @formatter:on
    }

    private PactDslJsonBody taskDtoBody(String taskState) {
        // @formatter:off
        return new PactDslJsonBody().
                stringValue("id", EXAMPLE_TASK_ID).
                datetime("due", "yyyy-MM-dd'T'HH:mm:ss", EXAMPLE_TASK_DUE).
                stringType("priority", EXAMPLE_TASK_PRIORITY).
                stringValue("state", taskState).
                stringType("type", ERROR_HANDLING_TASKTYPE_NAME).
                stringType("system", ERROR_HANDLING_SYSTEM_NAME).
                stringType("service", ERROR_HANDLING_SERVICE_NAME).
                eachLike("references").
                    stringType("name", EXAMPLE_TASK_REFERENCE_NAME).
                    stringType("uri", EXAMPLE_TASK_REFERENCE_URI).
                    close().
                eachLike("additionalDetails").
                    stringType("name", EXAMPLE_TASK_ADDITIONAL_INFO_NAME).
                    stringType("value", EXAMPLE_TASK_ADDITIONAL_INFO_VALUE).
                    close().
                asBody();
        // @formatter:on
    }

    private PactDslJsonBody taskStateDtoBody(String taskStatus) {
        // @formatter:off
        return new PactDslJsonBody().
                stringValue("state", taskStatus).
                asBody();
        // @formatter:on
    }

    private TaskTypDto taskTypeDto() {
        TaskTypeDisplayDto taskTypeDisplayDto = TaskTypeDisplayDto.builder()
                .language(EXAMPLE_LANGUAGE)
                .title(EXAMPLE_TITLE)
                .description(EXAMPLE_DESCRIPTION)
                .displayName(EXAMPLE_NAME_DISPLAY)
                .displayDomain(EXAMPLE_DOMAIN_DISPLAY)
                .build();
        return TaskTypDto.builder()
                .name(ERROR_HANDLING_TASKTYPE_NAME)
                .system(ERROR_HANDLING_SYSTEM_NAME)
                .domain(ERROR_HANDLING_DOMAIN_NAME)
                .display(List.of(taskTypeDisplayDto))
                .build();
    }

    private TaskDto taskDto() {
        TaskReferenceDto errorServiceReference = TaskReferenceDto.builder()
                .name(EXAMPLE_TASK_REFERENCE_NAME)
                .uri(EXAMPLE_TASK_REFERENCE_URI)
                .build();
        TaskDetailDto additionalTaskDetail = TaskDetailDto.builder()
                .name(EXAMPLE_TASK_ADDITIONAL_INFO_NAME)
                .value(EXAMPLE_TASK_ADDITIONAL_INFO_VALUE)
                .build();
        return TaskDto.builder()
                .id(UUID.fromString(EXAMPLE_TASK_ID))
                .type(ERROR_HANDLING_TASKTYPE_NAME)
                .system(ERROR_HANDLING_SYSTEM_NAME)
                .service(ERROR_HANDLING_SERVICE_NAME)
                .priority(EXAMPLE_TASK_PRIORITY)
                .due(LocalDateTime.ofInstant(EXAMPLE_TASK_DUE, ZoneId.systemDefault()))
                .state(TaskStatus.OPEN)
                .reference(errorServiceReference)
                .additionalDetail(additionalTaskDetail)
                .build();
    }

}
