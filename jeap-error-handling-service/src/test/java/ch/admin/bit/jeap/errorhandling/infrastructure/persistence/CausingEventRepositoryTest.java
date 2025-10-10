package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PersistenceTestConfig.class)
class CausingEventRepositoryTest {

    @Autowired
    private ErrorRepository errorRepository;
    @Autowired
    private CausingEventRepository causingEventRepository;
    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;


    @Test
    void findCausingEventIdsWithoutError_foundOne() {
        CausingEvent causingEvent1 = saveCausingEvent(getEventMetadata("event-id-1"));
        CausingEvent causingEvent2 = saveCausingEvent(getEventMetadata("event-id-2"));

        storeError(getEventMetadata("event-id-1"), causingEvent1, ZonedDateTime.now());

        Slice<UUID> errorList = causingEventRepository.findCausingEventIdsWithoutError(Pageable.ofSize(10));
        assertThat(errorList.getContent()).hasSize(1);
        assertThat(errorList.getContent().get(0)).isEqualTo(causingEvent2.getId());
    }

    @Test
    void findByCausingEventId_withHeader() {
        MessageHeader persistedHeader = MessageHeader.builder()
                .headerName("the-header")
                .headerValue("the-value".getBytes(UTF_8))
                .build();
        CausingEvent persistedCausingEvent = saveCausingEvent(getEventMetadata("event-id-1"), persistedHeader);

        storeError(getEventMetadata("event-id-1"), persistedCausingEvent, ZonedDateTime.now());

        Optional<CausingEvent> causingEventOptional = causingEventRepository.findByCausingEventId(persistedCausingEvent.getMetadata().getId());
        assertThat(causingEventOptional)
                .isNotEmpty();
        CausingEvent causingEvent = causingEventOptional.get();
        assertThat(causingEvent.getHeaders())
                .hasSize(1);
        MessageHeader header = causingEvent.getHeaders().get(0);
        assertThat(header.getHeaderName())
                .isEqualTo("the-header");
        assertThat(header.getHeaderValue())
                .isEqualTo("the-value".getBytes(UTF_8));
    }

    @Test
    void deleteAllHeadersByCausingEventIds() {
        MessageHeader persistedHeader = MessageHeader.builder()
                .headerName("the-header")
                .headerValue("the-value".getBytes(UTF_8))
                .build();
        CausingEvent persistedCausingEvent = saveCausingEvent(getEventMetadata("event-id-1"), persistedHeader);
        storeError(getEventMetadata("event-id-1"), persistedCausingEvent, ZonedDateTime.now());
        testEntityManager.flush();

        errorRepository.deleteAll();
        causingEventRepository.deleteAllHeadersByCausingEventIds(List.of(persistedCausingEvent.getId()));
        causingEventRepository.deleteAllById(List.of(persistedCausingEvent.getId()));

        testEntityManager.flush();

        Number headerCount = (Number) testEntityManager.getEntityManager()
                .createNativeQuery("select count(*) from message_header").getSingleResult();
        assertThat(headerCount.intValue())
                .isZero();
    }

    @Test
    void findCausingEventIdsWithoutError_foundNone() {
        CausingEvent causingEvent1 = saveCausingEvent(getEventMetadata("event-id-1"));
        CausingEvent causingEvent2 = saveCausingEvent(getEventMetadata("event-id-2"));

        storeError(getEventMetadata("event-id-1"), causingEvent1, ZonedDateTime.now());
        storeError(getEventMetadata("event-id-2"), causingEvent2, ZonedDateTime.now());

        Slice<UUID> errorList = causingEventRepository.findCausingEventIdsWithoutError(Pageable.ofSize(10));
        assertThat(errorList).isEmpty();
        assertThat(errorList.getContent()).isEmpty();
    }

    @Test
    void findCausingEventIdsWithoutError_foundMultiplePages() {
        saveCausingEvent(getEventMetadata("event-id-1"));
        saveCausingEvent(getEventMetadata("event-id-2"));
        saveCausingEvent(getEventMetadata("event-id-3"));

        Pageable pageable = Pageable.ofSize(1);
        Slice<UUID> resultPage = causingEventRepository.findCausingEventIdsWithoutError(pageable);
        assertSlice(resultPage, true);
        pageable = resultPage.nextPageable();
        resultPage = causingEventRepository.findCausingEventIdsWithoutError(pageable);
        assertSlice(resultPage, true);
        pageable = resultPage.nextPageable();
        resultPage = causingEventRepository.findCausingEventIdsWithoutError(pageable);
        assertSlice(resultPage, false);
    }

    private void assertSlice(Slice<UUID> slice, boolean hasNext) {
        assertThat(slice.hasNext()).isEqualTo(hasNext);
        assertThat(slice.getContent()).hasSize(1);
    }


    private CausingEvent saveCausingEvent(EventMetadata metadata, MessageHeader... messageHeaders) {
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(UTF_8))
                        .topic("topic")
                        .clusterName("clusterName")
                        .build())
                .metadata(metadata)
                .headers(List.of(messageHeaders))
                .build();
        return causingEventRepository.save(causingEvent);
    }

    private EventMetadata getEventMetadata(String eventId) {
        return EventMetadata.builder()
                .id(eventId)
                .created(ZonedDateTime.now())
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

    private void storeError(EventMetadata metadata, CausingEvent causingEvent, ZonedDateTime zonedDateTime) {
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
        errorRepository.save(error);
    }


}
