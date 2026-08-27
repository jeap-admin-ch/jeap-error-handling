package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.command.avro.AvroCommandBuilder;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.ModulithPublicationData;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaBeanNames;
import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandReferences;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandReferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
class ModulithPublicationCommandSender {

    private static final String TRANSACTIONAL_OUTBOX_BEAN_TYPE = "TransactionalOutbox";

    private final Map<String, TransactionalOutbox> outboxesByBeanName;
    private final KafkaProperties kafkaProperties;

    void retry(Error error) {
        ModulithPublicationData publication = error.getCausingEvent().getModulithPublication();
        outboxFor(publication).sendMessage(
                new RetryCommandBuilder(kafkaProperties, publication, error.getId().toString()).build(),
                publication.getRetryCommandTopic());
    }

    void discard(Error error, String reason) {
        ModulithPublicationData publication = error.getCausingEvent().getModulithPublication();
        outboxFor(publication).sendMessage(
                new DiscardCommandBuilder(kafkaProperties, publication, error.getId().toString(), reason).build(),
                publication.getDiscardCommandTopic());
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

        private RetryCommandBuilder(KafkaProperties properties, ModulithPublicationData publication, String errorId) {
            super(RetryModulithPublicationCommand::new);
            this.properties = properties;
            this.publication = publication;
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
                            "modulithPublication", publication.getPublicationId())));
            setPayload(new RetryModulithPublicationCommandPayload());
            return super.build();
        }
    }

    private static final class DiscardCommandBuilder
            extends AvroCommandBuilder<DiscardCommandBuilder, DiscardModulithPublicationCommand> {

        private final KafkaProperties properties;
        private final ModulithPublicationData publication;
        private final String reason;

        private DiscardCommandBuilder(KafkaProperties properties, ModulithPublicationData publication,
                String errorId, String reason) {
            super(DiscardModulithPublicationCommand::new);
            this.properties = properties;
            this.publication = publication;
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
                            "modulithPublication", publication.getPublicationId())));
            setPayload(new DiscardModulithPublicationCommandPayload(reason));
            return super.build();
        }
    }
}
