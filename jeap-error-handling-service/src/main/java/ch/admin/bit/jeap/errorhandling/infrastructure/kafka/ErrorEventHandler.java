package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;

public interface ErrorEventHandler {

    void handle(String clusterName, MessageProcessingFailedEvent errorEvent);

    default void handle(String clusterName, ModulithPublicationProcessingFailedEvent errorEvent) {
        throw new IllegalArgumentException("Modulith publication failures are not supported by this handler");
    }
}
