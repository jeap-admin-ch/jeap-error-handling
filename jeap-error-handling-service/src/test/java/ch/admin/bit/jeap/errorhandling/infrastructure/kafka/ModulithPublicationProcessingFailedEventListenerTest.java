package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ModulithPublicationProcessingFailedEventListenerTest {

    private static final String CLUSTER_NAME = "test-cluster";

    @Mock
    private ErrorEventHandler errorEventHandler;
    @Mock
    private Acknowledgment acknowledgment;
    @Mock
    private ModulithPublicationProcessingFailedEvent event;

    @Test
    void onMessage_shouldProcessModulithFailureAndAcknowledgeWhenSuccessful() {
        ModulithPublicationProcessingFailedEventListener listener =
                new ModulithPublicationProcessingFailedEventListener(errorEventHandler, CLUSTER_NAME);
        ConsumerRecord<Object, ModulithPublicationProcessingFailedEvent> consumerRecord =
                new ConsumerRecord<>("modulith-failure-topic", 0, 0L, null, event);

        listener.onMessage(consumerRecord, acknowledgment);

        verify(errorEventHandler).handle(CLUSTER_NAME, event);
        verify(acknowledgment).acknowledge();
    }
}
