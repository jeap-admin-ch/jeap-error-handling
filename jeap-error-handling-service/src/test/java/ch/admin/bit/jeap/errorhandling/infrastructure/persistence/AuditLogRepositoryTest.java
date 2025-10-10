package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog.AuditedAction.RESEND_CAUSING_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PersistenceTestConfig.class)
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ErrorRepository errorRepository;

    @Autowired
    private CausingEventRepository causingEventRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private Error error1;
    private Error error2;
    private Error error3;

    @BeforeEach
    void setUpErrors() {
        error1 = storeError("1");
        error2 = storeError("2");
        error3 = storeError("3");
    }

    @Test
    void testSaveAnFindById() {
        AuditLog auditLog = createAuditLog(error1);

        AuditLog auditLogSaved = auditLogRepository.save(auditLog);

        testEntityManager.flush(); // make sure the instance is written to the database
        testEntityManager.detach(auditLogSaved); // make sure the instance is read from the database on the next get

        Optional<AuditLog> auditLogReadOptional = auditLogRepository.findById(auditLog.getId());

        assertThat(auditLogReadOptional).isPresent();
        AuditLog auditLogRead = auditLogReadOptional.get();
        assertThat(auditLogRead.getId()).isEqualTo(auditLog.getId());
        assertThat(auditLogRead.getUser()).isEqualTo(auditLog.getUser());
        assertThat(auditLogRead.getAction()).isEqualTo(auditLog.getAction());
        assertThat(auditLogRead.getErrorId()).isEqualTo(auditLog.getErrorId());
        assertThat(auditLogRead.getCreated().truncatedTo(ChronoUnit.MILLIS).toInstant())
                .isEqualTo(auditLog.getCreated().truncatedTo(ChronoUnit.MILLIS).toInstant());
    }

    @Test
    void testFindByErrorId() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime before = now.minusMinutes(1);
        AuditLog auditLog1Now = auditLogRepository.save(createAuditLog(error1, now));
        AuditLog auditLog1Before = auditLogRepository.save(createAuditLog(error1, before));
        AuditLog auditLog2 = auditLogRepository.save(createAuditLog(error2));

        List<AuditLog> auditLogsForError1 = auditLogRepository.findAllByErrorId(error1.getId());
        assertThat(auditLogsForError1).containsExactly(auditLog1Now, auditLog1Before);

        List<AuditLog> auditLogsForError2 = auditLogRepository.findAllByErrorId(error2.getId());
        assertThat(auditLogsForError2).containsExactly(auditLog2);
    }

    @Test
    void testDeleteAllByErrorIdIn() {
        auditLogRepository.save(createAuditLog(error1));
        AuditLog auditLog2 = auditLogRepository.save(createAuditLog(error2));
        auditLogRepository.save(createAuditLog(error3));

        auditLogRepository.deleteAllByErrorIdIn(Set.of(error1.getId(), error3.getId()));

        List<AuditLog> allRemainingAuditLogs = auditLogRepository.findAll();
        assertThat(allRemainingAuditLogs).containsExactlyInAnyOrder(auditLog2);
    }

    private AuditLog createAuditLog(Error error) {
        return createAuditLog(error, ZonedDateTime.now());
    }

    private AuditLog createAuditLog(Error error, ZonedDateTime created) {
        return AuditLog.builder()
                .user(User.builder()
                        .authContext("USER")
                        .subject("glj45-823408-fas234")
                        .extId("12345-67890")
                        .familyName("Smith")
                        .givenName("John")
                        .build())
                .errorId(error.getId())
                .action(RESEND_CAUSING_EVENT)
                .created(created)
                .build();
    }

    private Error storeError(String causingEventId) {
        Error error = Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(saveCausingEvent(createEventMetadata(causingEventId)))
                .errorEventData(ErrorEventData.builder()
                        .code("errorCode")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .build())
                .errorEventMetadata(createEventMetadata(causingEventId + "-error-event"))
                .closingReason("")
                .created(ZonedDateTime.now())
                .build();
        return errorRepository.save(error);
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
        return causingEventRepository.save(causingEvent);
    }

    private EventMetadata createEventMetadata(String eventId) {
        return EventMetadata.builder()
                .id(eventId)
                .created(ZonedDateTime.now())
                .idempotenceId("idempotence-" + eventId)
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

}
