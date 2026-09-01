package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;

class MessageProcessingFailedEventListener extends AbstractErrorEventListener<MessageProcessingFailedEvent> {

    MessageProcessingFailedEventListener(ErrorEventHandler errorEventHandler, String clusterName) {
        super(errorEventHandler, clusterName, MessageProcessingFailedEvent.class);
    }

    @Override
    protected void consume(MessageProcessingFailedEvent errorEvent) {
        errorEventHandler.handle(clusterName, errorEvent);
    }
}
