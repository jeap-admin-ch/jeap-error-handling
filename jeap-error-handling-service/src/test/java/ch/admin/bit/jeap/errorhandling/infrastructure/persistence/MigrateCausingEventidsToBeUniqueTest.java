package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import db.migration.common.V5_0_0__MigrateCausingEventIdsToBeUnique;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@DataJpaTest
@Import(PersistenceTestConfig.class)
@ExtendWith(MockitoExtension.class)
class MigrateCausingEventidsToBeUniqueTest {

    @Autowired
    private ErrorRepository errorRepository;
    @Autowired
    private CausingEventRepository causingEventRepository;
    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Mock
    private Context context;

    @Test
    void migrate_expectDuplicateCausingEventsToBeRemoved() {
        // Drop constraint to be able to insert duplicate data to test the migration
        jdbcTemplate.execute("ALTER TABLE causing_event DROP CONSTRAINT unique_causing_event_metadata_id");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        doReturn(connection).when(context).getConnection();

        // Given: Duplicate causing event entities for same event ID
        EventMetadata eventMetadata = getEventMetadata("same-id");
        final CausingEvent causingEvent1 = saveCausingEvent(eventMetadata);
        final CausingEvent causingEvent2 = saveCausingEvent(eventMetadata);
        final CausingEvent causingEvent3 = saveCausingEvent(eventMetadata);
        EventMetadata eventMetadataDifferentId = getEventMetadata("different-id");
        final CausingEvent causingEventDifferentId = saveCausingEvent(eventMetadataDifferentId);

        // Given: Existing persistent errors referencing different duplicate causing event entities each
        Error error1 = storeError(eventMetadata, causingEvent1, now());
        Error error2 = storeError(eventMetadata, causingEvent2, now());
        Error error3 = storeError(eventMetadata, causingEvent3, now());
        Error errorDifferentId = storeError(eventMetadataDifferentId, causingEventDifferentId, now());

        // When: The migration is applied
        V5_0_0__MigrateCausingEventIdsToBeUnique migration = new V5_0_0__MigrateCausingEventIdsToBeUnique();
        migration.migrate(context);
        testEntityManager.flush();

        // Then: Expect two causing event entity to be present, and error entities referencing the same causing event entity
        List<CausingEvent> allCausingEvents = causingEventRepository.findAll();
        assertThat(allCausingEvents)
                .hasSize(2);
        assertThat(updatedErrorCausingEventId(error1.getId()))
                .isEqualTo(causingEvent1.getId());
        assertThat(updatedErrorCausingEventId(error2.getId()))
                .isEqualTo(causingEvent1.getId());
        assertThat(updatedErrorCausingEventId(error3.getId()))
                .isEqualTo(causingEvent1.getId());
        assertThat(updatedErrorCausingEventId(errorDifferentId.getId()))
                .isEqualTo(causingEventDifferentId.getId());
    }

    private UUID updatedErrorCausingEventId(UUID errorId) {
        return jdbcTemplate.queryForObject(
                "SELECT causing_event_id FROM error WHERE id = ?",
                UUID.class, errorId);
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
        return testEntityManager.persistAndFlush(causingEvent);
    }

    private EventMetadata getEventMetadata(String eventId) {
        return EventMetadata.builder()
                .id(eventId)
                .created(now())
                .idempotenceId("idem")
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

    private Error storeError(EventMetadata metadata, CausingEvent causingEvent, ZonedDateTime zonedDateTime) {
        Error error = Error.builder()
                .state(Error.ErrorState.PERMANENT)
                .causingEvent(causingEvent)
                .errorEventData(ErrorEventData.builder()
                        .code("123")
                        .temporality(ErrorEventData.Temporality.PERMANENT)
                        .message("test")
                        .build())
                .errorEventMetadata(metadata)
                .closingReason("")
                .created(zonedDateTime)
                .build();
        return testEntityManager.persistAndFlush(error);
    }
}
