package ch.admin.bit.jeap.errorhandling.domain.housekeeping;

import ch.admin.bit.jeap.errorhandling.ErrorHandlingITBase;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.UUID;

import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.RESEND_CAUSING_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

class HouseKeepingServiceIT extends ErrorHandlingITBase {

    @Autowired
    private HouseKeepingService houseKeepingService;
    @Autowired
    private HouseKeepingServiceConfigProperties configProperties;

    @Test
    void testCleanup() {
        configProperties.setPageSize(1);
        configProperties.setMaxPages(1000);

        // given
        saveScheduledResend(saveError(ZonedDateTime.now().minusYears(1)));
        saveScheduledResend(saveError(ZonedDateTime.now().minusYears(1)));
        saveScheduledResend(saveError(ZonedDateTime.now().minusYears(1)));
        saveScheduledResend(saveError(ZonedDateTime.now()));
        assertThat(errorRepository.count()).isEqualTo(4);
        assertThat(causingEventRepository.count()).isEqualTo(4);
        assertThat(scheduledResendRepository.count()).isEqualTo(4);
        assertThat(auditLogRepository.count()).isEqualTo(4);
        assertThat(errorGroupRepository.count()).isEqualTo(4);

        // when
        houseKeepingService.cleanup();

        // then
        assertThat(errorRepository.count()).isEqualTo(1);
        assertThat(causingEventRepository.count()).isEqualTo(1);
        assertThat(scheduledResendRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.count()).isEqualTo(1);
        assertThat(errorGroupRepository.count()).isEqualTo(1);
    }

    @Test
    void testCleanup_maxPages() {
        configProperties.setPageSize(1);
        configProperties.setMaxPages(2);

        // given
        saveScheduledResend(saveError(ZonedDateTime.now().minusYears(1)));
        saveScheduledResend(saveError(ZonedDateTime.now().minusYears(1)));
        saveScheduledResend(saveError(ZonedDateTime.now().minusYears(1)));
        saveScheduledResend(saveError(ZonedDateTime.now()));
        assertThat(errorRepository.count()).isEqualTo(4);
        assertThat(causingEventRepository.count()).isEqualTo(4);
        assertThat(scheduledResendRepository.count()).isEqualTo(4);
        assertThat(auditLogRepository.count()).isEqualTo(4);

        // when
        houseKeepingService.cleanup();

        // then
        assertThat(errorRepository.count()).isEqualTo(2);
        assertThat(causingEventRepository.count()).isEqualTo(2);
        assertThat(scheduledResendRepository.count()).isEqualTo(2);
        assertThat(auditLogRepository.count()).isEqualTo(2);
        assertThat(errorGroupRepository.count()).isEqualTo(2);
    }

    private UUID saveError(ZonedDateTime created) {
        EventMetadata metadata = getEventMetadata();
        CausingEvent causingEvent = saveCausingEvent(metadata);
        return storeError(metadata, causingEvent, created, createErrorGroup());
    }

    private void saveScheduledResend(UUID errorId) {
        ScheduledResend scheduledResend = new ScheduledResend(errorId, ZonedDateTime.now().plusDays(1));
        scheduledResendRepository.save(scheduledResend);
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

    private EventMetadata getEventMetadata() {
        return EventMetadata.builder()
                .id(UUID.randomUUID().toString())
                .created(ZonedDateTime.now())
                .idempotenceId(UUID.randomUUID().toString())
                .publisher(EventPublisher.builder()
                        .service("service")
                        .system("system")
                        .build())
                .type(EventType.builder()
                        .name("name")
                        .version("1.0.0")
                        .build())
                .build();
    }

    private UUID storeError(EventMetadata metadata, CausingEvent causingEvent, ZonedDateTime created, ErrorGroup errorGroup) {
        Error error = Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code("123")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .stackTrace("stacktrace")
                        .stackTraceHash(UUID.randomUUID().toString())
                        .build())
                .errorEventMetadata(metadata)
                .closingReason("")
                .created(created)
                .errorGroup(errorGroup)
                .build();
        errorRepository.save(error);
        addAuditLog(error);
        return error.getId();
    }

    private void addAuditLog(Error error) {
        auditLogRepository.save(AuditLog.builder()
                .user(User.builder()
                        .authContext("USER")
                        .subject("glj45-823408-fas234")
                        .extId("12345-67890")
                        .familyName("Smith")
                        .givenName("John")
                        .build())
                .errorId(error.getId())
                .action(RESEND_CAUSING_EVENT)
                .created(error.getCreated())
                .build());
    }

    private ErrorGroup createErrorGroup() {
        String randomString = UUID.randomUUID().toString();
        ErrorGroup errorGroup = new ErrorGroup(randomString, randomString, randomString, randomString, randomString);
        return errorGroupRepository.save(errorGroup);
    }
}
