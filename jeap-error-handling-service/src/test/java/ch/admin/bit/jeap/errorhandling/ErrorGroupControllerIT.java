package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.web.api.*;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

class ErrorGroupControllerIT extends ErrorHandlingITBase {
    public static final String ALL_GROUP_URL = "/api/error-group";
    public static final String UPDATE_TICKET_URL = "/api/error-group/update-ticket-number";
    public static final String UPDATE_FREETEXT_URL = "/api/error-group/update-free-text";
    private static final SemanticApplicationRole VIEW_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("errorgroup")
            .operation("view")
            .build();
    private static final SemanticApplicationRole EDIT_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("errorgroup")
            .operation("edit")
            .build();
    private static final String SUBJECT = "69368608-D736-43C8-5F76-55B7BF168299";
    private static final JeapAuthenticationContext CONTEXT = JeapAuthenticationContext.SYS;
    private final RequestSpecification apiSpec;

    public ErrorGroupControllerIT(@Value("${server.port}") int serverPort) {
        apiSpec = new RequestSpecBuilder()
                .setPort(serverPort).build();
    }

    @BeforeEach
    public void setUp() {
        errorGroupRepository.deleteAll();
    }

    @Test
    void shouldReturnEmptyGroupListWhenNoGroupExists() {
        ErrorGroupResponse response = given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                contentType("application/json").
                when().
                post(ALL_GROUP_URL).
                then().
                statusCode(HttpStatus.OK.value()).extract().as(ErrorGroupResponse.class);
        Assertions.assertThat(response.totalErrorGroupCount()).isZero();
        Assertions.assertThat(response.groups()).isEmpty();

    }

    @Test
    void shouldReturnSingleGroupWhenGroupExists() {
        // given
        ErrorGroup createdGroup = createErrorGroup("111", "eventName1", "source1", "nullpointer1",  "stackTraceHash1");
        errorGroupRepository.save(createdGroup);
        Error error = createError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        error.setErrorGroup(createdGroup);
        errorRepository.save(error);
        // when
        ErrorGroupResponse response = given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                contentType("application/json").
                when().
                post(ALL_GROUP_URL).
                then().
                statusCode(HttpStatus.OK.value()).extract().as(ErrorGroupResponse.class);
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(1);
        Assertions.assertThat(response.groups()).hasSize(1);
        ErrorGroupDTO dto = response.groups().getFirst();
        Assertions.assertThat(dto.errorCount()).isEqualTo(1L);
        Assertions.assertThat(dto.errorEvent()).isEqualTo("eventName1");
        Assertions.assertThat(dto.errorPublisher()).isEqualTo("source1");
        Assertions.assertThat(dto.errorMessage()).isEqualTo("nullpointer1");
        Assertions.assertThat(dto.stackTraceHash()).isEqualTo("stackTraceHash1");

    }

    @Test
    void shouldReturnPaginationResults_whenMultipleGroupExists() {
        // given
        List<ErrorGroup> groups = List.of(
                createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1"),
                createErrorGroup("222", "eventName2", "source2", "nullpointer2", "stackTraceHash2"),
                createErrorGroup("333", "eventName3", "source3", "nullpointer3", "stackTraceHash3")
        );
        errorGroupRepository.saveAll(groups);
        groups.forEach(errorGroup -> {
            errorRepository.save(createError(errorGroup));
            errorRepository.save(createError(errorGroup));
        });

        // when
        ErrorGroupResponse response = given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                queryParam("pageSize", 2).
                queryParam("pageIndex", 0).
                contentType("application/json").
                when().
                post(ALL_GROUP_URL).
                then().
                statusCode(HttpStatus.OK.value()).extract().as(ErrorGroupResponse.class);
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(3);
        Assertions.assertThat(response.groups()).hasSize(2);
    }

    @Test
    void shouldReturnGroupsMatchingSearchCriteria_whenNoTicketIsSet() {
        // given
        List<ErrorGroup> groups = List.of(
                createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1"),
                createErrorGroup("222", "eventName2", "source2", "nullpointer2", "stackTraceHash2"),
                createErrorGroup("333", "eventName3", "source3", "nullpointer3", "stackTraceHash3")
        );
        errorGroupRepository.saveAll(groups);
        groups.forEach(errorGroup -> {
            errorRepository.save(createError(errorGroup));
            errorRepository.save(createError(errorGroup));
        });

        ErrorGroupSearchFormDto searchForm = new ErrorGroupSearchFormDto();
        searchForm.setNoTicket(true);

        // when
        ErrorGroupResponse response = getAllGroupsWithBody(searchForm);

        //then
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(3);

        //when
        groups.getFirst().setTicketNumber("ABC-123");
        errorGroupRepository.save(groups.getFirst());

        //then
        response = getAllGroupsWithBody(searchForm);
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(2);
    }

    @Test
    void shouldReturnGroupsMatchingSearchCriteria_whenDateRangeIsSet() {
        // given
        List<ErrorGroup> groups = List.of(
                createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1"),
                createErrorGroup("222", "eventName2", "source2", "nullpointer2", "stackTraceHash2"),
                createErrorGroup("333", "eventName3", "source3", "nullpointer3", "stackTraceHash3")
        );
        errorGroupRepository.saveAll(groups);
        // All error have creation date 3 days ago
        groups.forEach(errorGroup -> {
            errorRepository.save(createError(errorGroup));
            errorRepository.save(createError(errorGroup));
        });

        ErrorGroupSearchFormDto searchForm = new ErrorGroupSearchFormDto();
        // when #1 - date range includes all errors
        searchForm.setDateFrom(ZonedDateTime.now().minusDays(4).toString());
        ErrorGroupResponse response = getAllGroupsWithBody(searchForm);

        //then #1
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(3);

        // when #2 - date range excludes all errors
        searchForm.setDateFrom(ZonedDateTime.now().minusDays(2).toString());
        response = getAllGroupsWithBody(searchForm);

        // then #2
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(0);

        // when #3 - date range excludes all errors
        searchForm.setDateFrom(ZonedDateTime.now().toString());
        searchForm.setDateTo(ZonedDateTime.now().minusDays(5).toString());
        response = getAllGroupsWithBody(searchForm);

        // then #3
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(0);
    }

    @Test
    void shouldReturnGroupsMatchingSearchCriteria_whenStringFiltersAreSet() {
        // given
        List<ErrorGroup> groups = List.of(
                createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1"),
                createErrorGroup("222", "eventName2", "source2", "nullpointer2", "stackTraceHash2"),
                createErrorGroup("333", "eventName3", "source3", "nullpointer3", "stackTraceHash3")
        );
        errorGroupRepository.saveAll(groups);
        // All error have creation date 3 days ago
        groups.forEach(errorGroup -> {
            errorRepository.save(createError(errorGroup));
            errorRepository.save(createError(errorGroup));
        });

        ErrorGroupSearchFormDto searchForm = new ErrorGroupSearchFormDto();
        // when #1 -
        searchForm.setMessageType("eventName1");
        ErrorGroupResponse response = getAllGroupsWithBody(searchForm);

        //then #1
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(1);

        searchForm.setMessageType(null);
        searchForm.setSource("source2");
        response = getAllGroupsWithBody(searchForm);
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(1);

    }

    @Test
    void shouldReturnGroupsMatchingSearchCriteria_whenFilterHasEmptyStrings() {
        // given
        List<ErrorGroup> groups = List.of(
                createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1"),
                createErrorGroup("222", "eventName2", "source2", "nullpointer2", "stackTraceHash2"),
                createErrorGroup("333", "eventName3", "source3", "nullpointer3", "stackTraceHash3")
        );
        errorGroupRepository.saveAll(groups);
        groups.forEach(errorGroup -> {
            errorRepository.save(createError(errorGroup));
            errorRepository.save(createError(errorGroup));
        });

        ErrorGroupSearchFormDto searchForm = new ErrorGroupSearchFormDto();
        searchForm.setMessageType("");
        searchForm.setSource("");
        ErrorGroupResponse response = getAllGroupsWithBody(searchForm);
        Assertions.assertThat(response.totalErrorGroupCount()).isEqualTo(3);

    }

    private ErrorGroupResponse getAllGroupsWithBody(ErrorGroupSearchFormDto searchForm) {
        return given().
                spec(apiSpec).
                auth().oauth2(createAuthTokenForUserRoles(VIEW_ROLE)).
                contentType("application/json").
                body(searchForm).
                when().
                post(ALL_GROUP_URL).
                then().
                statusCode(HttpStatus.OK.value()).extract().as(ErrorGroupResponse.class);
    }

    @Test
    void shouldUpdateTicketNumber_WhenValidRequest() {
        // given
        Error error = createError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        ErrorGroup errorGroup = createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1");
        ErrorGroup group = errorGroupRepository.save(errorGroup);
        error.setErrorGroup(group);
        errorRepository.save(error);

        UpdateTicketNumberRequest request = new UpdateTicketNumberRequest();
        request.setErrorGroupId(group.getId().toString());
        request.setTicketNumber("TAPAS-745");
        // when
        ErrorGroupDTO response = given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(EDIT_ROLE)).
                contentType("application/json").
                body(request).
                when().
                post(UPDATE_TICKET_URL).
                then().
                statusCode(HttpStatus.OK.value()).
                extract().
                as(ErrorGroupDTO.class);
        // then
        Assertions.assertThat(response.ticketNumber()).isEqualTo("TAPAS-745");
        Assertions.assertThat(response.errorGroupId()).isEqualTo(group.getId().toString());
    }

    @Test
    void shouldUpdateTicketNumber_InvalidUUID() {
        // given
        Error error = createError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        ErrorGroup errorGroup = createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1");
        ErrorGroup group = errorGroupRepository.save(errorGroup);
        error.setErrorGroup(group);
        errorRepository.save(error);
        UpdateTicketNumberRequest request = new UpdateTicketNumberRequest();
        request.setErrorGroupId("invalid-uuid");
        request.setTicketNumber("TAPAS-745");
        // when
        given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(EDIT_ROLE)).
                contentType("application/json").
                body(request).
                when().
                post(UPDATE_TICKET_URL).
                then().
                statusCode(400);
    }

    @Test
    void shouldUpdateFreeText_WhenErrorGroupDoesNotExist() {
        // given
        UpdateFreeTextRequest request = new UpdateFreeTextRequest();
        request.setErrorGroupId(UUID.randomUUID().toString());
        request.setFreeText("new issue");
        // when
        given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(EDIT_ROLE)).
                contentType("application/json").
                body(request).
                when().
                post(UPDATE_FREETEXT_URL).
                then().
                statusCode(400);
    }

    @Test
    void shouldUpdateFreeText_WhenValidRequest() {
        // given
        Error error = createError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        ErrorGroup errorGroup = createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1");
        ErrorGroup group = errorGroupRepository.save(errorGroup);
        error.setErrorGroup(group);
        errorRepository.save(error);
        UpdateFreeTextRequest request = new UpdateFreeTextRequest();
        request.setErrorGroupId(group.getId().toString());
        request.setFreeText("new issue");
        // when
        ErrorGroupDTO response = given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(EDIT_ROLE)).
                contentType("application/json").
                body(request).
                when().
                post(UPDATE_FREETEXT_URL).
                then().
                statusCode(HttpStatus.OK.value()).
                extract().
                as(ErrorGroupDTO.class);
        // then
        Assertions.assertThat(response.freeText()).isEqualTo("new issue");
        Assertions.assertThat(response.errorGroupId()).isEqualTo(group.getId().toString());
    }

    @Test
    void shouldUpdateFreeText_InvalidUUID() {
        // given
        Error error = createError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        ErrorGroup errorGroup = createErrorGroup("111", "eventName1", "source1", "nullpointer1", "stackTraceHash1");
        ErrorGroup group = errorGroupRepository.save(errorGroup);
        error.setErrorGroup(group);
        errorRepository.save(error);
        UpdateFreeTextRequest request = new UpdateFreeTextRequest();
        request.setErrorGroupId("invalid-uuid");
        request.setFreeText("new issue");
        // when
        given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(EDIT_ROLE)).
                contentType("application/json").
                body(request).
                when().
                post(UPDATE_FREETEXT_URL).
                then().
                statusCode(400);
    }

    @Test
    void shouldUpdateTicketNumber_WhenErrorGroupDoesNotExist() {
        // given
        UpdateTicketNumberRequest request = new UpdateTicketNumberRequest();
        request.setErrorGroupId(UUID.randomUUID().toString());
        request.setTicketNumber("TAPAS-745");
        // when
        given().
                spec(apiSpec).
                auth().
                oauth2(createAuthTokenForUserRoles(EDIT_ROLE)).
                contentType("application/json").
                body(request).
                when().
                post(UPDATE_TICKET_URL).
                then().
                statusCode(400);
    }

    private Error createError(ErrorGroup errorGroup) {
        Error error = createError(Error.ErrorState.PERMANENT, "service", "eventName", ZonedDateTime.now().minusDays(3), "123", "myTraceId");
        error.setErrorGroup(errorGroup);
        return error;
    }

    private ErrorGroup createErrorGroup(String errorCode, String eventName, String publisher, String errorMessage, String stackTracehash) {
        return new ErrorGroup(errorCode, eventName, publisher, errorMessage, stackTracehash);
    }

    private String createAuthTokenForUserRoles(SemanticApplicationRole... userroles) {
        return jwsBuilderFactory.createValidForFixedLongPeriodBuilder(SUBJECT, CONTEXT).
                withUserRoles(userroles).
                build().serialize();
    }

    @SuppressWarnings("SameParameterValue")
    private Error createError(Error.ErrorState errorState, String serviceName, String eventName, ZonedDateTime created, String errorCode, String traceId) {
        EventMetadata metadata = getEventMetadata(serviceName, eventName);
        CausingEvent causingEvent = saveCausingEvent(metadata);
        return createError(metadata, causingEvent, errorState, created, errorCode, traceId);
    }

    private EventMetadata getEventMetadata(String serviceName, String eventName) {
        return EventMetadata.builder()
                .id(UUID.randomUUID().toString())
                .created(ZonedDateTime.now())
                .idempotenceId(UUID.randomUUID().toString())
                .publisher(EventPublisher.builder()
                        .service(serviceName)
                        .system("service")
                        .build())
                .type(EventType.builder()
                        .name(eventName)
                        .version("1.0.0")
                        .build())
                .build();
    }

    private CausingEvent saveCausingEvent(EventMetadata metadata) {
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(StandardCharsets.UTF_8))
                        .topic("topic")
                        .clusterName("clusterName")
                        .build())
                .metadata(metadata)
                .build();
        causingEventRepository.save(causingEvent);
        return causingEvent;
    }

    private Error createError(EventMetadata metadata, CausingEvent causingEvent, Error.ErrorState temporaryRetried, ZonedDateTime created, String errorCode, String traceId) {
        return Error.builder()
                .state(temporaryRetried)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code(errorCode)
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .stackTrace(traceId)
                        .build())
                .errorEventMetadata(metadata)
                .closingReason("because this is a test")
                .originalTraceContext(OriginalTraceContext.builder()
                        .traceIdString(traceId)
                        .build())
                .errorGroup(null)
                .created(created)
                .build();
    }
}
