package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;

public interface ErrorEventHandler {

    void handle(String clusterName, MessageProcessingFailedEvent errorEvent);
}
