package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ModulithPublicationData;
import ch.admin.bit.jeap.messaging.avro.security.AvroClassSecurity;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModulithPublicationCommandSenderTest {

    @Mock
    private TransactionalOutbox defaultOutbox;
    @Mock
    private TransactionalOutbox awsOutbox;
    @Mock
    private KafkaProperties kafkaProperties;
    @Mock
    private KafkaFailedEventResender kafkaResender;

    private Error error;
    private CausingEvent causingEvent;
    private FailedEventResender resender;

    @BeforeAll
    static void installAvroClassSecurity() {
        AvroClassSecurity.installDefaultIfMissing();
    }

    @BeforeEach
    void setUp() {
        when(kafkaProperties.getDefaultClusterName()).thenReturn("bit");
        causingEvent = org.mockito.Mockito.mock(CausingEvent.class);
        when(causingEvent.getOrigin()).thenReturn(CausingEvent.Origin.MODULITH_PUBLICATION);
        error = org.mockito.Mockito.mock(Error.class);
        when(error.getCausingEvent()).thenReturn(causingEvent);
        ModulithPublicationCommandSender commandSender = new ModulithPublicationCommandSender(Map.of(
                "kafkaTransactionalOutbox", defaultOutbox,
                "awsKafkaTransactionalOutbox", awsOutbox), kafkaProperties);
        resender = new FailedEventResender(kafkaResender, commandSender);
    }

    @Test
    void sendsRetryCommandToPublicationTopic() {
        stubCommandMetadata();
        useCluster("aws");

        resender.resend(error);

        ArgumentCaptor<RetryModulithPublicationCommand> command = ArgumentCaptor.forClass(RetryModulithPublicationCommand.class);
        verify(awsOutbox).sendMessage(command.capture(), org.mockito.ArgumentMatchers.eq("retry-topic"));
        verify(defaultOutbox, never()).sendMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(kafkaResender, never()).resend(error);
        assertEquals("publication-id", command.getValue().getReferences().getPublication().getPublicationId());
        assertEquals("retry:0b1ae887-815c-4e0c-b3e4-dd20ade03359",
                command.getValue().getIdentity().getIdempotenceId());
    }

    @Test
    void sendsDiscardCommandWithReasonToPublicationTopic() {
        stubCommandMetadata();
        useCluster("aws");

        resender.discardIfModulith(error, "resolved manually");

        ArgumentCaptor<DiscardModulithPublicationCommand> command = ArgumentCaptor.forClass(DiscardModulithPublicationCommand.class);
        verify(awsOutbox).sendMessage(command.capture(), org.mockito.ArgumentMatchers.eq("discard-topic"));
        verify(defaultOutbox, never()).sendMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        assertEquals("publication-id", command.getValue().getReferences().getPublication().getPublicationId());
        assertEquals("resolved manually", command.getValue().getPayload().getReason());
        assertEquals("discard:0b1ae887-815c-4e0c-b3e4-dd20ade03359",
                command.getValue().getIdentity().getIdempotenceId());
    }

    @Test
    void sendsCommandToDefaultClusterOutbox() {
        stubCommandMetadata();
        useCluster("bit");

        resender.resend(error);

        verify(defaultOutbox).sendMessage(org.mockito.ArgumentMatchers.any(RetryModulithPublicationCommand.class),
                org.mockito.ArgumentMatchers.eq("retry-topic"));
        verify(awsOutbox, never()).sendMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUnknownClusterWithoutDefaultFallback() {
        useCluster("unknown");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> resender.resend(error));

        assertEquals("No transactional outbox configured for Kafka cluster 'unknown'", exception.getMessage());
        verify(defaultOutbox, never()).sendMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(awsOutbox, never()).sendMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private void useCluster(String clusterName) {
        ModulithPublicationData publication = ModulithPublicationData.builder()
                .clusterName(clusterName)
                .publicationId("publication-id")
                .listener("listener-id")
                .eventType("example.Event")
                .retryCommandTopic("retry-topic")
                .discardCommandTopic("discard-topic")
                .build();
        when(causingEvent.getModulithPublication()).thenReturn(publication);
    }

    private void stubCommandMetadata() {
        when(kafkaProperties.getSystemName()).thenReturn("test-system");
        when(kafkaProperties.getServiceName()).thenReturn("test-service");
        when(error.getId()).thenReturn(UUID.fromString("0b1ae887-815c-4e0c-b3e4-dd20ade03359"));
    }
}
