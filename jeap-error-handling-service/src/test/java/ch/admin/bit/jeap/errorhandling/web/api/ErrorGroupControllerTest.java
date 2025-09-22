package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorSearchService;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.domain.exceptions.InvalidUuidException;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedData;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedDataList;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedDataRecord;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializer;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import ch.admin.bit.jeap.security.test.resource.configuration.ServletJeapAuthorizationConfig;
import ch.admin.bit.jeap.security.test.resource.extension.WithAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("unused")
@ActiveProfiles("error-group-controller-test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ErrorGroupControllerTest.TestConfiguration.class)
@WebMvcTest(ErrorGroupController.class)
class ErrorGroupControllerTest {

    private static final String DATE_TIME_DTO_STRING = "2024-09-23 15:30:14";
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.of(2024, 9, 23, 15, 30, 14, 0, ZoneId.systemDefault());
    private static final String PROFILE = "error-group-controller-test";
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
    private static final SemanticApplicationRole INVALID_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("errorgroup")
            .operation("invalid")
            .build();
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    private ErrorGroupService errorGroupService;
    @MockitoBean
    private ErrorService errorService;
    @MockitoBean
    private ErrorSearchService errorSearchService;
    @MockitoBean
    private KafkaProperties kafkaProperties;
    @Autowired
    private ErrorGroupController errorGroupController;
    @MockitoBean
    private ScheduledResendService scheduledResendService;
    @MockitoBean
    private AuditLogService auditLogService;
    @MockitoBean
    KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;
    @MockitoBean
    private DomainEventDeserializer domainEventDeserializer;
    private ErrorGroupAggregatedDataRecord errorGroupAggregatedData;
    private ErrorGroupAggregatedDataList errorGroupAggregatedDataList;

    private JeapAuthenticationToken viewRoleToken() {
        return createAuthenticationForUserRoles(VIEW_ROLE);
    }

    private JeapAuthenticationToken editRoleToken() {
        return createAuthenticationForUserRoles(EDIT_ROLE);
    }

    private JeapAuthenticationToken invalidRoleToken() {
        return createAuthenticationForUserRoles(INVALID_ROLE);
    }

    private JeapAuthenticationToken createAuthenticationForUserRoles(SemanticApplicationRole... userroles) {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(userroles).build();
    }

    @BeforeEach
    void setUp() {
        errorGroupAggregatedData = ErrorGroupAggregatedDataRecord.builder().
                groupId(UUID.randomUUID()).
                errorCount(42L).
                errorEvent("JmeSomethingCreatedEvent").
                errorPublisher("jme-some-service").
                errorCode("some-code").
                errorMessage("some-message").
                firstErrorAt(DATE_TIME).
                latestErrorAt(DATE_TIME).
                ticketNumber("TAPAS-745").
                freeText("some-text").
                stackTraceHash("some-hash").
                build();
        errorGroupAggregatedDataList = new ErrorGroupAggregatedDataList(1, List.of(errorGroupAggregatedData));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getGroups_shouldReturnErrorGroupResponse() {
        // Arrange
        Mockito.doReturn(errorGroupAggregatedDataList)
                .when(errorGroupService)
                .findErrorGroupAggregatedData(Mockito.any(ErrorGroupSearchCriteria.class));
        // Act
        ErrorGroupResponse response = errorGroupController.getGroups( 0, 10, null);
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.totalErrorGroupCount()).isEqualTo(1);
        assertThat(response.groups()).hasSize(1);
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getGroups_shouldMapErrorGroupInfoToDTO() {
        // Arrange
        Mockito.doReturn(errorGroupAggregatedDataList)
                .when(errorGroupService)
                .findErrorGroupAggregatedData(Mockito.any(ErrorGroupSearchCriteria.class));

        // Act
        ErrorGroupResponse response = errorGroupController.getGroups(0, 10, null);
        // Assert
        ErrorGroupDTO dto = response.groups().getFirst();
        assertThat(dto).isNotNull();
        assertThat(dto.errorGroupId()).isEqualTo(errorGroupAggregatedData.getGroupId().toString());
        assertThat(dto.errorCount()).isEqualTo(errorGroupAggregatedData.getErrorCount());
        assertThat(dto.errorEvent()).isEqualTo(errorGroupAggregatedData.getErrorEvent());
        assertThat(dto.errorPublisher()).isEqualTo(errorGroupAggregatedData.getErrorPublisher());
        assertThat(dto.errorCode()).isEqualTo(errorGroupAggregatedData.getErrorCode());
        assertThat(dto.errorMessage()).isEqualTo(errorGroupAggregatedData.getErrorMessage());
        assertThat(dto.ticketNumber()).isEqualTo(errorGroupAggregatedData.getTicketNumber());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getGroups_shouldUseDefaultPageIndexAndSize() {
        // Arrange
        Mockito.doReturn(errorGroupAggregatedDataList)
                .when(errorGroupService)
                .findErrorGroupAggregatedData(Mockito.any(ErrorGroupSearchCriteria.class));
        // Act
        ErrorGroupResponse response = errorGroupController.getGroups( 0,10, null);
        // Assert
        assertThat(response.totalErrorGroupCount()).isEqualTo(1);
        assertThat(response.groups()).hasSize(1);
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getGroups_shouldUseProvidedPageIndexAndSize() {
        // Arrange
        Mockito.doReturn(errorGroupAggregatedDataList)
                .when(errorGroupService)
                .findErrorGroupAggregatedData(Mockito.any(ErrorGroupSearchCriteria.class));
        // Act
        ErrorGroupResponse response = errorGroupController.getGroups( 2, 20, null);
        // Assert
        assertThat(response.totalErrorGroupCount()).isEqualTo(1);
        assertThat(response.groups()).hasSize(1);
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getGroups_shouldHandleEmptyResults() {
        // Arrange
        ErrorGroupAggregatedDataList errorGroupAggregatedDataList = new ErrorGroupAggregatedDataList(0L, List.of());
        Mockito.doReturn(errorGroupAggregatedDataList)
                .when(errorGroupService)
                .findErrorGroupAggregatedData(Mockito.any(ErrorGroupSearchCriteria.class));
        // Act
        ErrorGroupResponse response = errorGroupController.getGroups( 0, 10, null);
        // Assert
        assertThat(response.totalErrorGroupCount()).isZero();
        assertThat(response.groups()).isEmpty();
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getGroups_shouldHandleMultipleResults() {
        // Arrange
        ErrorGroupAggregatedData errorGroupAggregatedDataOther = ErrorGroupAggregatedDataRecord.builder().
            groupId(UUID.randomUUID()).
            errorCount(7L).
            errorEvent("JmeSomethingOtherCreatedEvent").
            errorPublisher("jme-some-other-service").
            errorCode("some-other-code").
            errorMessage("some-other-message").
            firstErrorAt(DATE_TIME).
            latestErrorAt(DATE_TIME).
            ticketNumber("TAPAS-123").
            freeText("some-other-text").
            build();

        ErrorGroupAggregatedDataList multipleList = new ErrorGroupAggregatedDataList(2L, List.of(errorGroupAggregatedData, errorGroupAggregatedDataOther));
        Mockito.doReturn(multipleList)
                .when(errorGroupService)
                .findErrorGroupAggregatedData(Mockito.any(ErrorGroupSearchCriteria.class));
        // Act
        ErrorGroupResponse response = errorGroupController.getGroups(0, 10, null);
        // Assert
        assertThat(response.totalErrorGroupCount()).isEqualTo(2);
        assertThat(response.groups()).hasSize(2);
        assertThat(response.groups().get(0).errorGroupId()).isEqualTo(errorGroupAggregatedData.getGroupId().toString());
        assertThat(response.groups().get(1).errorGroupId()).isEqualTo(errorGroupAggregatedDataOther.getGroupId().toString());
    }

    // unit tests for updateTicketNumber
    @Test
    @WithAuthentication("editRoleToken")
    void testUpdateTicketNumberSuccess() {
        // Arrange
        final UUID groupId = UUID.randomUUID();
        UpdateTicketNumberRequest updateTicketNumberRequest = new UpdateTicketNumberRequest(groupId.toString(), "TAPAS-745");
        ErrorGroupAggregatedData errorGroupAggregatedData = Mockito.mock(ErrorGroupAggregatedData.class);
        Mockito.when(errorGroupAggregatedData.getGroupId()).thenReturn(groupId);
        Mockito.when(errorGroupAggregatedData.getErrorCount()).thenReturn(10L);
        Mockito.when(errorGroupAggregatedData.getErrorEvent()).thenReturn("MessageProcessingFailedEvent");
        Mockito.when(errorGroupAggregatedData.getErrorPublisher()).thenReturn("wvs-communication-service");
        Mockito.when(errorGroupAggregatedData.getErrorCode()).thenReturn("MESSAGE_NOT_FOUND");
        Mockito.when(errorGroupAggregatedData.getErrorMessage()).thenReturn("MESSAGE_NOT_FOUND: No message found with dbMessageId=61314b01-a46a-4269-97d5-fab5690045f2");
        Mockito.when(errorGroupAggregatedData.getFirstErrorAt()).thenReturn(DATE_TIME);
        Mockito.when(errorGroupAggregatedData.getLatestErrorAt()).thenReturn(null);
        Mockito.when(errorGroupAggregatedData.getTicketNumber()).thenReturn("TAPAS-745");
        Mockito.when(errorGroupAggregatedData.getFreeText()).thenReturn("known issue");
        Mockito.when(errorGroupService.getErrorGroupAggregatedData(groupId)).thenReturn(errorGroupAggregatedData);

        // Act
        ResponseEntity<ErrorGroupDTO> response = errorGroupController.updateTicketNumber(updateTicketNumberRequest);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        ErrorGroupDTO dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.errorGroupId()).isEqualTo(groupId.toString());
        assertThat(dto.errorCount()).isEqualTo(10L);
        assertThat(dto.errorEvent()).isEqualTo("MessageProcessingFailedEvent");
        assertThat(dto.errorPublisher()).isEqualTo("wvs-communication-service");
        assertThat(dto.errorCode()).isEqualTo("MESSAGE_NOT_FOUND");
        assertThat(dto.errorMessage()).isEqualTo("MESSAGE_NOT_FOUND: No message found with dbMessageId=61314b01-a46a-4269-97d5-fab5690045f2");
        assertThat(dto.firstErrorAt()).isEqualTo(DATE_TIME_DTO_STRING);
        assertThat(dto.latestErrorAt()).isNull();
        assertThat(dto.ticketNumber()).isEqualTo("TAPAS-745");
        Mockito.verify(errorGroupService, Mockito.times(1)).updateTicketNumber(groupId, "TAPAS-745");
    }

    @Test
    @WithAuthentication("editRoleToken")
    void testUpdateTicketNumber_InvalidUUID() {
        UpdateTicketNumberRequest request = new UpdateTicketNumberRequest("invalid-uuid", "TAPAS-745");
        Assertions.assertThrows(InvalidUuidException.class, ()-> errorGroupController.updateTicketNumber(request));
    }

    @Test
    @WithAuthentication("editRoleToken")
    void testUpdateTicketNumber_ExceptionHandling() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        UpdateTicketNumberRequest updateTicketNumberRequest = new UpdateTicketNumberRequest(uuid.toString(), "TAPAS-745");
        Mockito.when(errorGroupService.updateTicketNumber(uuid, "TAPAS-745")).thenThrow(new RuntimeException("Unexpected exception"));
        // Act and Assert
        Assertions.assertThrows(RuntimeException.class, () -> errorGroupController.updateTicketNumber(updateTicketNumberRequest));
    }


    // unit tests for updateFreetext
    @Test
    @WithAuthentication("editRoleToken")
    void testUpdateFreeTextSuccess() {
        // Arrange
        final UUID groupId = UUID.randomUUID();
        UpdateFreeTextRequest updateFreeTextRequest = new UpdateFreeTextRequest(groupId.toString(), "known issue");
        ErrorGroupAggregatedData errorGroupAggregatedData = Mockito.mock(ErrorGroupAggregatedData.class);
        Mockito.when(errorGroupAggregatedData.getGroupId()).thenReturn(groupId);
        Mockito.when(errorGroupAggregatedData.getErrorCount()).thenReturn(10L);
        Mockito.when(errorGroupAggregatedData.getErrorEvent()).thenReturn("MessageProcessingFailedEvent");
        Mockito.when(errorGroupAggregatedData.getErrorPublisher()).thenReturn("wvs-communication-service");
        Mockito.when(errorGroupAggregatedData.getErrorCode()).thenReturn("MESSAGE_NOT_FOUND");
        Mockito.when(errorGroupAggregatedData.getErrorMessage()).thenReturn("MESSAGE_NOT_FOUND: No message found with dbMessageId=61314b01-a46a-4269-97d5-fab5690045f2");
        Mockito.when(errorGroupAggregatedData.getFirstErrorAt()).thenReturn(DATE_TIME);
        Mockito.when(errorGroupAggregatedData.getLatestErrorAt()).thenReturn(null);
        Mockito.when(errorGroupAggregatedData.getTicketNumber()).thenReturn("");
        Mockito.when(errorGroupAggregatedData.getFreeText()).thenReturn("known issue");
        Mockito.when(errorGroupService.getErrorGroupAggregatedData(groupId)).thenReturn(errorGroupAggregatedData);

        // Act
        ResponseEntity<ErrorGroupDTO> response = errorGroupController.updateFreeText(updateFreeTextRequest);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        ErrorGroupDTO dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.errorGroupId()).isEqualTo(groupId.toString());
        assertThat(dto.errorCount()).isEqualTo(10L);
        assertThat(dto.errorEvent()).isEqualTo("MessageProcessingFailedEvent");
        assertThat(dto.errorPublisher()).isEqualTo("wvs-communication-service");
        assertThat(dto.errorCode()).isEqualTo("MESSAGE_NOT_FOUND");
        assertThat(dto.errorMessage()).isEqualTo("MESSAGE_NOT_FOUND: No message found with dbMessageId=61314b01-a46a-4269-97d5-fab5690045f2");
        assertThat(dto.firstErrorAt()).isEqualTo(DATE_TIME_DTO_STRING);
        assertThat(dto.latestErrorAt()).isNull();
        assertThat(dto.freeText()).isEqualTo("known issue");
        Mockito.verify(errorGroupService, Mockito.times(1)).updateFreeText(groupId, "known issue");
    }

    @Test
    @WithAuthentication("editRoleToken")
    void testUpdateFreeText_InvalidUUID() {
        UpdateFreeTextRequest updateFreeTextRequest = new UpdateFreeTextRequest("invalid-uuid", "known issue");
        Assertions.assertThrows(InvalidUuidException.class, ()-> errorGroupController.updateFreeText(updateFreeTextRequest));
    }

    @Test
    @WithAuthentication("editRoleToken")
    void testUpdateFreeText_ExceptionHandling() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        UpdateFreeTextRequest updateFreeTextRequest = new UpdateFreeTextRequest(uuid.toString(), "known issue");
        Mockito.when(errorGroupService.updateFreeText(uuid, "known issue")).thenThrow(new RuntimeException("Unexpected exception"));
        // Act and Assert
        Assertions.assertThrows(RuntimeException.class, () -> errorGroupController.updateFreeText(updateFreeTextRequest));
    }

    @Test
    @WithAuthentication("editRoleToken")
    void testCreateIssueSuccess() {
        final String expectedIssueId = errorGroupAggregatedData.getTicketNumber();
        final UUID groupId = errorGroupAggregatedData.getGroupId();
        Mockito.when(errorGroupService.createIssue(groupId)).thenReturn(expectedIssueId);
        Mockito.when(errorGroupService.getErrorGroupAggregatedData(groupId)).thenReturn(errorGroupAggregatedData);

        ResponseEntity<ErrorGroupDTO> response = errorGroupController.createIssue(groupId);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        ErrorGroupDTO dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.errorGroupId()).isEqualTo(groupId.toString());
        assertThat(dto.ticketNumber()).isEqualTo(expectedIssueId);
        Mockito.verify(errorGroupService, Mockito.times(1)).createIssue(groupId);
        Mockito.verify(errorGroupService, Mockito.times(1)).getErrorGroupAggregatedData(groupId);
    }

    @Test
    @WithAuthentication("editRoleToken")
    void testCreateIssue_ExceptionHandling() {
        UUID groupId = UUID.randomUUID();
        Mockito.when(errorGroupService.createIssue(groupId)).thenThrow(new RuntimeException("Issue creation failed"));

        Assertions.assertThrows(RuntimeException.class, () -> errorGroupController.createIssue(groupId));
        Mockito.verify(errorGroupService, Mockito.times(1)).createIssue(groupId);
        Mockito.verify(errorGroupService, Mockito.never()).getErrorGroupAggregatedData(groupId);
    }

    @Test
    @WithAuthentication("invalidRoleToken")
    void testForbiddenView() throws Exception {
        mockMvc.perform(post("/api/error-group"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void testForbiddenEdit() throws Exception {
        mockMvc.perform(post("/api/error-group/update-ticket-number")
                .content("{\"key\":\"value\"}")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isForbidden());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void testForbiddenCreateIssue() throws Exception {
        UUID groupId = UUID.randomUUID();
        mockMvc.perform(post("/api/error-group/" + groupId + "/issue"))
                .andExpect(status().isForbidden());
    }


    @Profile(PROFILE) // prevent other tests using class path scanning picking up this configuration
    @Configuration
    @ComponentScan
    static class TestConfiguration extends ServletJeapAuthorizationConfig {

        // You have to provide the system name and the application context to the test support base class.
        TestConfiguration(ApplicationContext applicationContext) {
            super("jme", applicationContext);
        }
    }

}
