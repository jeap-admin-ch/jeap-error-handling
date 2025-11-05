package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.ErrorStubs;
import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorList;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorSearchService;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializer;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaDeadLetterBatchConsumerProducer;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.ResendClusterProvider;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.SemanticApplicationRole;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationContext;
import ch.admin.bit.jeap.security.resource.token.JeapAuthenticationToken;
import ch.admin.bit.jeap.security.test.resource.JeapAuthenticationTestTokenBuilder;
import ch.admin.bit.jeap.security.test.resource.configuration.ServletJeapAuthorizationConfig;
import ch.admin.bit.jeap.security.test.resource.extension.WithAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.DELETE_ERROR;
import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.RESEND_CAUSING_EVENT;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unused")
@ActiveProfiles("error-controller-test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ErrorControllerTest.TestConfiguration.class)
@WebMvcTest(ErrorController.class)
class ErrorControllerTest {

    private static final String PROFILE = "error-controller-test";
    @MockitoBean
    private ErrorRepository errorRepository;

    @MockitoBean
    KafkaDeadLetterBatchConsumerProducer kafkaDeadLetterBatchConsumerProducer;

    @Profile(PROFILE) // prevent other tests using class path scanning picking up this configuration
    @Configuration
    @ComponentScan
    static class TestConfiguration extends ServletJeapAuthorizationConfig {

        // You have to provide the system name and the application context to the test support base class.
        TestConfiguration(ApplicationContext applicationContext) {
            super("jme", applicationContext);
        }
    }

    @MockitoBean
    private ErrorService errorService;

    @MockitoBean
    private ErrorGroupService errorGroupService;

    @MockitoBean
    private ErrorSearchService errorSearchService;

    @MockitoBean
    private KafkaProperties kafkaProperties;

    @Autowired
    private ErrorController errorController;

    @MockitoBean
    private ScheduledResendService scheduledResendService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private DomainEventDeserializer domainEventDeserializer;

    @MockitoBean
    private ResendClusterProvider resendClusterProvider;

    private static final SemanticApplicationRole VIEW_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("error")
            .operation("view")
            .build();

    private static final SemanticApplicationRole DELETE_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("error")
            .operation("delete")
            .build();

    private static final SemanticApplicationRole RETRY_ROLE = SemanticApplicationRole.builder()
            .system("jme")
            .resource("error")
            .operation("retry")
            .build();

    @BeforeEach
    void setUp() {
        doReturn("default").when(resendClusterProvider).getResendClusterNameFor(any());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void findErrors_nullSafe() {
        // given
        final var errorSearchFormDto = ErrorSearchFormDto.builder().build();
        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(new ErrorList(0, List.of()));

        // when & then
        assertThatCode(() -> errorController.findErrors(0, 10, errorSearchFormDto))
                .doesNotThrowAnyException();
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void listPermanentErrors() {
        // given
        Error permanentError = ErrorStubs.createPermanentError();
        int totalElements = 2;
        ErrorList errorList = new ErrorList(totalElements, singletonList(permanentError));
        doReturn(errorList).when(errorService).getPermanentErrorList(0, 10);

        // when
        ErrorListDTO response = errorController.listPermanentErrors(0, 10);

        // then
        assertEquals(totalElements, response.getTotalErrorCount());
        assertEquals(1, response.getErrors().size());
        ErrorDTO errorDTO = response.getErrors().getFirst();
        assertEquals(permanentError.getId().toString(), errorDTO.getId());
        assertEquals(ErrorStubs.ERROR_CODE, errorDTO.getErrorCode());
        assertEquals(ErrorStubs.ERROR_MESSAGE, errorDTO.getErrorMessage());
        assertEquals("PERMANENT", errorDTO.getErrorState());
        assertEquals(ErrorStubs.EVENT_NAME, errorDTO.getEventName());
        assertEquals(ErrorStubs.EVENT_PUBLISHER_SERVICE, errorDTO.getEventPublisher());
        assertEquals(ErrorStubs.TIMESTAMP, errorDTO.getTimestamp());
        assertNull(errorDTO.getStacktrace());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getErrorDetails_forPermanentError() {
        // given
        UUID errorId = stubPermanentError();
        final User user = getPamsUser();
        stubTwoAuditLogs(errorId, user);

        // when
        ErrorDTO response = errorController.getErrorDetails(errorId);

        // then
        assertEquals(errorId.toString(), response.getId());
        assertEquals(0, response.getErrorCountForEvent());
        assertNull(response.getNextResendTimestamp());
        assertFalse(response.isCanDelete());
        assertFalse(response.isCanRetry());
        assertNotNull(response.getStacktrace());
        assertEquals("topic, Partition 42, Offset 303", response.getEventTopicDetails());
        assertEquals("default", response.getEventClusterName());
        assertNotNull(response.getAuditLogDTOs());
        assertEquals(2, response.getAuditLogDTOs().size());
        assertAuditLogDTO(response.getAuditLogDTOs().getFirst(), DELETE_ERROR, user);
        assertAuditLogDTO(response.getAuditLogDTOs().get(1), RESEND_CAUSING_EVENT, user);
        assertEquals("some-ticket-number", response.getTicketNumber());
        assertEquals("some-free-text", response.getFreeText());
        assertTrue(response.isSigned());
        assertEquals("6A 65 61 70 2D 63 65 72 74 2D 76 61 6C 75 65", response.getJeapCert());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void getErrorDetails_forTemporaryError() {
        // given
        Error errorStub = ErrorStubs.createTemporaryError();
        final User user = getMicroserviceUser();
        stubTwoAuditLogs(errorStub.getId(), user);
        UUID errorId = errorStub.getId();
        ZonedDateTime timestamp = ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");
        doReturn(errorStub).when(errorService).getError(errorId);
        doReturn(timestamp).when(scheduledResendService).getNextResendTimestamp(errorId);

        // when
        ErrorDTO response = errorController.getErrorDetails(errorId);

        // then
        assertEquals(errorId.toString(), response.getId());
        assertEquals(0, response.getErrorCountForEvent());
        assertEquals("2007-12-03 10:15:30", response.getNextResendTimestamp());
        assertFalse(response.isCanDelete());
        assertFalse(response.isCanRetry());
        assertNotNull(response.getStacktrace());
        assertNotNull(response.getAuditLogDTOs());
        assertEquals(2, response.getAuditLogDTOs().size());
        assertAuditLogDTO(response.getAuditLogDTOs().getFirst(), DELETE_ERROR, user);
        assertAuditLogDTO(response.getAuditLogDTOs().get(1), RESEND_CAUSING_EVENT, user);
    }

    private void assertAuditLogDTO(final AuditLogDTO auditLogDTO, final AuditedAction action, User user) {
        assertEquals(user.getAuthContext(), auditLogDTO.getAuthContext());
        assertEquals(user.getSubject(), auditLogDTO.getSubject());
        assertEquals(user.getExtId(), auditLogDTO.getExtId());
        assertEquals(user.getFamilyName(), auditLogDTO.getFamilyName());
        assertEquals(user.getGivenName(), auditLogDTO.getGivenName());
        assertEquals(action, auditLogDTO.getAction());
        assertNotNull(auditLogDTO.getCreated());
    }

    @Test
    @WithAuthentication("viewAndRetryRoleToken")
    void getErrorDetails_expectRetryRoleCanRetry() {
        // given
        UUID errorId = stubPermanentError();
        stubEmptyAuditLog(errorId);

        // when
        ErrorDTO response = errorController.getErrorDetails(errorId);

        // then
        assertFalse(response.isCanDelete());
        assertTrue(response.isCanRetry());
        assertTrue(response.getAuditLogDTOs().isEmpty());
    }

    @Test
    @WithAuthentication("viewAndDeleteRoleToken")
    void getErrorDetails_expectDeleteRoleCanDelete() {
        // given
        UUID errorId = stubPermanentError();
        stubEmptyAuditLog(errorId);

        // when
        ErrorDTO response = errorController.getErrorDetails(errorId);

        // then
        assertTrue(response.isCanDelete());
        assertFalse(response.isCanRetry());
        assertTrue(response.getAuditLogDTOs().isEmpty());
    }

    @Test
    @WithAuthentication("retryRoleToken")
    void deleteEvent_expectNotAllowedForRetryRole() {
        // given
        UUID errorId = stubPermanentError();

        // when
        assertThrows(AccessDeniedException.class, () ->
                errorController.deleteError(errorId, ""));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void deleteEvent_expectNotAllowedForViewRole() {
        // given
        UUID errorId = stubPermanentError();

        // when
        assertThrows(AccessDeniedException.class, () ->
                errorController.deleteError(errorId, ""));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void retryEvent_expectNotAllowedForViewRole() {
        // given
        UUID errorId = stubPermanentError();

        // when
        assertThrows(AccessDeniedException.class, () ->
                errorController.retryEvent(errorId));

    }

    @Test
    @WithAuthentication("retryRoleToken")
    void retryEvent_expectAllowedForRetryRole() {
        // given
        UUID errorId = stubPermanentError();

        // when
        errorController.retryEvent(errorId);

        verify(errorService).manualResend(errorId);
    }

    @Test
    @WithAuthentication("retryRoleToken")
    void testRetryEventList() {
        UUID errorId1 = UUID.randomUUID();
        UUID errorId2 = UUID.randomUUID();
        List<UUID> errorIds = Arrays.asList(errorId1, errorId2);

        errorController.retryEventList(errorIds);

        verify(errorService).manualResend(errorId1);
        verify(errorService).manualResend(errorId2);
    }

    @Test
    @WithAuthentication("deleteRoleToken")
    void testDeleteErrorList() {
        UUID errorId1 = UUID.randomUUID();
        UUID errorId2 = UUID.randomUUID();
        List<UUID> errorIds = Arrays.asList(errorId1, errorId2);
        String reason = "because this is a test";

        errorController.deleteErrorList(errorIds, reason);

        verify(errorService).delete(errorId1, reason);
        verify(errorService).delete(errorId2, reason);
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void whenTicketNumberExists_thenReturnResult() {
        // Given
        final var errorSearchFormDto = ErrorSearchFormDto.builder().ticketNumber("TAPAS-745").build();
        Error permanentError = ErrorStubs.createPermanentError();
        int totalElements = 1;
        ErrorList errorList = new ErrorList(totalElements, singletonList(permanentError));
        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(errorList);

        //when
        ErrorListDTO result = errorController.findErrors(0, 10, errorSearchFormDto);

        //Then
        assertNotNull(result);
        assertEquals(errorList.getTotalElements(), result.getTotalErrorCount());
        verify(errorSearchService, times(1)).search(any(ErrorSearchCriteria.class));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void whenTicketNumberDoesNotExist_thenReturnEmptyResult() {
        // Given
        final var errorSearchFormDto = ErrorSearchFormDto.builder().ticketNumber("XYZ-745").build();
        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(new ErrorList(0, List.of()));

        //when
        ErrorListDTO result = errorController.findErrors(0, 10, errorSearchFormDto);

        //Then
        assertNotNull(result);
        assertEquals(0, result.getTotalErrorCount());
        assertTrue(result.getErrors().isEmpty());
        verify(errorSearchService, times(1)).search(any(ErrorSearchCriteria.class));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void whenTicketNumberIsNull_thenHandleGracefully() {
        // Given
        final var errorSearchFormDto = ErrorSearchFormDto.builder().ticketNumber(null).build();
        Error permanentError = ErrorStubs.createPermanentError();
        int totalElements = 1;
        ErrorList errorList = new ErrorList(totalElements, singletonList(permanentError));
        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(errorList);

        //when
        ErrorListDTO result = errorController.findErrors(0, 10, errorSearchFormDto);

        //Then
        assertNotNull(result);
        assertEquals(1, result.getTotalErrorCount());
        verify(errorSearchService, times(1)).search(any(ErrorSearchCriteria.class));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void whenTicketNumberIsValidButNoResults_thenReturnEmpty() {
        // Given
        final var errorSearchFormDto = ErrorSearchFormDto.builder().ticketNumber("TAPAS-745").build();
        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(new ErrorList(0, List.of()));

        //when
        ErrorListDTO result = errorController.findErrors(0, 10, errorSearchFormDto);

        //Then
        assertNotNull(result);
        assertEquals(0, result.getTotalErrorCount());
        assertTrue(result.getErrors().isEmpty());
        verify(errorSearchService, times(1)).search(any(ErrorSearchCriteria.class));
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void testFindErrorWithTicketNumber() {
        // Given
        final var errorSearchFormDto = ErrorSearchFormDto.builder().ticketNumber("TAPAS-745").build();

        int totalElements = 1;
        ErrorList errorList = new ErrorList(totalElements, singletonList(mockError()));

        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(errorList);

        //when
        ErrorListDTO result = errorController.findErrors(0, 10, errorSearchFormDto);

        //Then
        assertNotNull(result);
        assertEquals(1, result.getTotalErrorCount());
        assertEquals("TAPAS-745", result.getErrors().getFirst().getTicketNumber());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void testEagerFetchingOfErrorGroup() {
        Error error = mockError();
        when(errorRepository.search(any(ErrorSearchCriteria.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(error)));

        int totalElements = 1;
        ErrorList errorList = new ErrorList(totalElements, singletonList(error));

        when(errorSearchService.search(any(ErrorSearchCriteria.class))).thenReturn(errorList);

        assertNotNull(errorList.getErrors().getFirst().getErrorGroup().getTicketNumber());
        assertEquals("TAPAS-745", errorList.getErrors().getFirst().getErrorGroup().getTicketNumber());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void testErrorSearchCriteriaBuilder() {
        ErrorSearchCriteria criteria = ErrorSearchCriteria.builder().ticketNumber("TAPAS-745").build();
        assertTrue(criteria.getTicketNumber().isPresent());
        assertEquals("TAPAS-745", criteria.getTicketNumber().get());
    }

    @Test
    @WithAuthentication("viewRoleToken")
    void findErrorsByGroupId_returnsExpectedResults() {
        // Given
        UUID groupId = UUID.randomUUID();
        ErrorGroupListSearchFormDto searchFormDto = ErrorGroupListSearchFormDto.builder().build();
        Error error = ErrorStubs.createPermanentError();
        int totalElements = 1;
        ErrorList errorList = new ErrorList(totalElements, List.of(error));
        when(errorService.getErrorListByGroupId(eq(groupId), any(ErrorGroupListSearchCriteria.class)))
                .thenReturn(errorList);

        // When
        ErrorListDTO result = errorController.findErrorsByGroupId(groupId, 0, 10, searchFormDto);

        // Then
        assertNotNull(result);
        assertEquals(totalElements, result.getTotalErrorCount());
        assertEquals(1, result.getErrors().size());
        assertEquals(error.getId().toString(), result.getErrors().getFirst().getId());
        verify(errorService, times(1)).getErrorListByGroupId(eq(groupId), any(ErrorGroupListSearchCriteria.class));
    }


    private Error mockError() {
        Error permanentError = ErrorStubs.createPermanentError();
        ErrorGroup group = new ErrorGroup();
        group.setTicketNumber("TAPAS-745");
        permanentError.setErrorGroup(group);
        return permanentError;
    }


    private JeapAuthenticationToken viewRoleToken() {
        return createAuthenticationForUserRoles(VIEW_ROLE);
    }

    private JeapAuthenticationToken retryRoleToken() {
        return createAuthenticationForUserRoles(RETRY_ROLE);
    }

    private JeapAuthenticationToken deleteRoleToken() {
        return createAuthenticationForUserRoles(DELETE_ROLE);
    }

    private JeapAuthenticationToken viewAndDeleteRoleToken() {
        return createAuthenticationForUserRoles(VIEW_ROLE, DELETE_ROLE);
    }

    private JeapAuthenticationToken viewAndRetryRoleToken() {
        return createAuthenticationForUserRoles(VIEW_ROLE, RETRY_ROLE);
    }

    private UUID stubPermanentError() {
        Error errorStub = ErrorStubs.createPermanentError();
        ErrorGroup errorGroup = ErrorGroup.from(errorStub);
        errorGroup.setFreeText("some-free-text");
        errorGroup.setTicketNumber("some-ticket-number");
        errorStub.setErrorGroup(errorGroup);
        UUID errorId = errorStub.getId();
        doReturn(errorStub).when(errorService).getError(errorId);
        return errorId;
    }

    private void stubEmptyAuditLog(UUID errorId) {
        when(auditLogService.getAuditLogs(errorId)).thenReturn(List.of());
    }

    private void stubTwoAuditLogs(UUID errorId, User user) {
        final ZonedDateTime now = ZonedDateTime.now();
        when(auditLogService.getAuditLogs(errorId)).thenReturn(List.of(
                AuditLog.builder()
                        .errorId(errorId)
                        .user(user)
                        .id(UUID.randomUUID())
                        .action(DELETE_ERROR)
                        .created(now)
                        .build(),
                AuditLog.builder()
                        .errorId(errorId)
                        .user(user)
                        .id(UUID.randomUUID())
                        .action(RESEND_CAUSING_EVENT)
                        .created(now.minusDays(1))
                        .build()));
    }

    // user context
    private User getPamsUser() {
        return User.builder()
                .authContext(JeapAuthenticationContext.USER.name())
                .subject("12345-abcde")
                .extId("qrstuvwxy-09876")
                .familyName("Doe")
                .givenName("John")
                .build();
    }

    // system context
    private User getMicroserviceUser() {
        return User.builder()
                .authContext(JeapAuthenticationContext.SYS.name())
                .subject("12345-abcde")
                .build();
    }

    private JeapAuthenticationToken createAuthenticationForUserRoles(SemanticApplicationRole... userroles) {
        return JeapAuthenticationTestTokenBuilder.create().withUserRoles(userroles).build();
    }

}
