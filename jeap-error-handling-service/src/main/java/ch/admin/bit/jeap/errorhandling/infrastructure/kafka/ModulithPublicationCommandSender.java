package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.command.avro.AvroCommandBuilder;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ModulithPublicationData;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaBeanNames;
import ch.admin.bit.jeap.messaging.model.Message;
import ch.admin.bit.jeap.messaging.transactionaloutbox.config.TransactionalOutboxConfigurationProperties;
import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandReferences;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandReferences;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
class ModulithPublicationCommandSender {

    private static final String TRANSACTIONAL_OUTBOX_BEAN_TYPE = "TransactionalOutbox";

    private final Map<String, TransactionalOutbox> outboxesByBeanName;
    private final KafkaProperties kafkaProperties;
    private final TransactionalOutboxConfigurationProperties outboxProperties;

    void retry(Error error) {
        ModulithPublicationData publication = error.getCausingEvent().getModulithPublication();
        sendCommand(outboxFor(publication),
                new RetryCommandBuilder(kafkaProperties, publication, error.getId().toString(),
                        error.getErrorEventMetadata().getId()).build(),
                publication.getRetryCommandTopic(), error);
    }

    void discard(Error error, String reason) {
        ModulithPublicationData publication = error.getCausingEvent().getModulithPublication();
        sendCommand(outboxFor(publication),
                new DiscardCommandBuilder(kafkaProperties, publication, error.getId().toString(),
                        error.getErrorEventMetadata().getId(), reason).build(),
                publication.getDiscardCommandTopic(), error);
    }

    private void sendCommand(TransactionalOutbox outbox, Message command, String topic, Error error) {
        if (outboxProperties.isHeadersEnabled()) {
            Headers headers = new RecordHeaders()
                    .add("jeap_eh_target_service", error.getErrorEventMetadata().getPublisher().getService()
                            .getBytes(StandardCharsets.UTF_8))
                    .add("jeap_eh_error_handling_service", kafkaProperties.getServiceName().getBytes(StandardCharsets.UTF_8));
            outbox.sendMessage(command, null, topic, headers);
        } else {
            outbox.sendMessage(command, topic);
        }
    }

    private TransactionalOutbox outboxFor(ModulithPublicationData publication) {
        String clusterName = publication.getClusterName();
        String beanName = new JeapKafkaBeanNames(kafkaProperties.getDefaultClusterName())
                .getBeanName(clusterName, TRANSACTIONAL_OUTBOX_BEAN_TYPE);
        TransactionalOutbox outbox = outboxesByBeanName.get(beanName);
        if (outbox == null) {
            throw new IllegalStateException("No transactional outbox configured for Kafka cluster '" + clusterName + "'");
        }
        return outbox;
    }

    private static final class RetryCommandBuilder
            extends AvroCommandBuilder<RetryCommandBuilder, RetryModulithPublicationCommand> {

        private final KafkaProperties properties;
        private final ModulithPublicationData publication;
        private final String failureEventId;

        private RetryCommandBuilder(KafkaProperties properties, ModulithPublicationData publication, String errorId,
                String failureEventId) {
            super(RetryModulithPublicationCommand::new);
            this.properties = properties;
            this.publication = publication;
            this.failureEventId = failureEventId;
            idempotenceId("retry:" + errorId);
        }

        @Override
        protected String getServiceName() {
            return properties.getServiceName();
        }

        @Override
        protected String getSystemName() {
            return properties.getSystemName();
        }

        @Override
        protected RetryCommandBuilder self() {
            return this;
        }

        @Override
        public RetryModulithPublicationCommand build() {
            setReferences(new RetryModulithPublicationCommandReferences(
                    new ch.admin.bit.jeap.modulith.command.retrypublication.ModulithPublicationReference(
                            "modulithPublication", publication.getPublicationId(), failureEventId)));
            setPayload(new RetryModulithPublicationCommandPayload());
            return super.build();
        }
    }

    private static final class DiscardCommandBuilder
            extends AvroCommandBuilder<DiscardCommandBuilder, DiscardModulithPublicationCommand> {

        private final KafkaProperties properties;
        private final ModulithPublicationData publication;
        private final String failureEventId;
        private final String reason;

        private DiscardCommandBuilder(KafkaProperties properties, ModulithPublicationData publication,
                String errorId, String failureEventId, String reason) {
            super(DiscardModulithPublicationCommand::new);
            this.properties = properties;
            this.publication = publication;
            this.failureEventId = failureEventId;
            this.reason = reason;
            idempotenceId("discard:" + errorId);
        }

        @Override
        protected String getServiceName() {
            return properties.getServiceName();
        }

        @Override
        protected String getSystemName() {
            return properties.getSystemName();
        }

        @Override
        protected DiscardCommandBuilder self() {
            return this;
        }

        @Override
        public DiscardModulithPublicationCommand build() {
            setReferences(new DiscardModulithPublicationCommandReferences(
                    new ch.admin.bit.jeap.modulith.command.discardpublication.ModulithPublicationReference(
                            "modulithPublication", publication.getPublicationId(), failureEventId)));
            setPayload(new DiscardModulithPublicationCommandPayload(reason));
            return super.build();
        }
    }
}
