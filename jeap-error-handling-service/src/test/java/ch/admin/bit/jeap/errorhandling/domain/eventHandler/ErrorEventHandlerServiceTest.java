package ch.admin.bit.jeap.errorhandling.domain.eventHandler;

import ch.admin.bit.jeap.errorhandling.TestMessageProcessingException;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.*;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ErrorEventData.Temporality;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEventBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import static ch.admin.bit.jeap.messaging.avro.errorevent.MessageHandlerExceptionInformation.Temporality.PERMANENT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(PersistenceTestConfig.class)
@ExtendWith(MockitoExtension.class)
class ErrorEventHandlerServiceTest {

    @Autowired
    private CausingEventRepository causingEventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Mock
    private ErrorService errorServiceMock;
    @Mock
    private ErrorEventMapper errorEventMapperMock;
    @Mock
    private Error errorMock;
    @Mock
    private ErrorEventData errorEventData;

    @Test
    void handle() {
        MessageProcessingFailedEvent failedEvent = createMessageProcessingFailedEvent();
        CausingEvent causingEvent = createCausingEvent(createEventMetadata());
        doReturn(causingEvent).when(errorEventMapperMock).toCausingEvent("testcluster", failedEvent);
        doReturn(errorMock).when(errorEventMapperMock).toError(any(), any());
        doReturn(errorEventData).when(errorMock).getErrorEventData();
        doReturn(Temporality.TEMPORARY).when(errorEventData).getTemporality();

        ErrorEventHandlerService errorEventHandlerService = new ErrorEventHandlerService(errorServiceMock,
                causingEventRepository, errorEventMapperMock, transactionManager);

        errorEventHandlerService.handle("testcluster", failedEvent);

        verify(errorServiceMock).handleTemporaryError(errorMock);
        assertTrue(causingEventRepository.findByCausingEventId(causingEvent.getMetadata().getId()).isPresent());
    }

    private MessageProcessingFailedEvent createMessageProcessingFailedEvent() {
        ConsumerRecord<?, ?> record = new ConsumerRecord<>("Topic", 1, 1, null, "payload");
        TestMessageProcessingException eventHandleException = new TestMessageProcessingException(PERMANENT, "500", "Payload");
        return MessageProcessingFailedEventBuilder.create()
                .eventHandleException(eventHandleException)
                .serviceName("service")
                .systemName("system")
                .originalMessage(record, null)
                .build();
    }

    private CausingEvent createCausingEvent(EventMetadata metadata) {
        CausingEvent causingEvent = CausingEvent.builder()
                .message(EventMessage.builder()
                        .offset(1)
                        .payload("test".getBytes(StandardCharsets.UTF_8))
                        .topic("topic")
                        .clusterName("clusterName")
                        .build())
                .metadata(metadata)
                .build();
        return causingEvent;
    }

    private EventMetadata createEventMetadata() {
        return EventMetadata.builder()
                .id("id")
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
}
