package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.domainevent.avro.error.EventProcessingFailedEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters.ProcessingFailedEventConverter;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;

@RequiredArgsConstructor
class MessageProcessingFailedEventListener implements AcknowledgingMessageListener<Object, SpecificRecordBase> {

    private final ProcessingFailedEventConverter processingFailedEventConverter;
    private final ErrorEventHandler errorEventHandler;
    private final String clusterName;

    @Override
    public void onMessage(ConsumerRecord<Object, SpecificRecordBase> data, Acknowledgment acknowledgment) {
        consume(data.value());
        acknowledgment.acknowledge();
    }

    /**
     * Due Backward-Compatibility the KafkaListener has to deal with the 'old' EventProcessingFailedEvent from
     * jeap-domain-library and the new MessageProcessingFailedEvent for jeap-messaging-Library.
     * <p>
     * In case of an error, the event will be published in the DLT, which is configured using the property
     * jeap.errorhandling.deadLetterTopicName
     */
    private void consume(SpecificRecordBase errorEvent) {
        MessageProcessingFailedEvent messageProcessingFailedEvent;
        if (errorEvent instanceof EventProcessingFailedEvent) {
            messageProcessingFailedEvent = processingFailedEventConverter.convert((EventProcessingFailedEvent) errorEvent);
        } else {
            messageProcessingFailedEvent = (MessageProcessingFailedEvent) errorEvent;
        }
        errorEventHandler.handle(clusterName, messageProcessingFailedEvent);
    }

}
