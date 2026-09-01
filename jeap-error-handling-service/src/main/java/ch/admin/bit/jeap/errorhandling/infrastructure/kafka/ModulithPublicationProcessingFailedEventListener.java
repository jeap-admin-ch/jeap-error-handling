package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;

class ModulithPublicationProcessingFailedEventListener
        extends AbstractErrorEventListener<ModulithPublicationProcessingFailedEvent> {

    ModulithPublicationProcessingFailedEventListener(ErrorEventHandler errorEventHandler, String clusterName) {
        super(errorEventHandler, clusterName, ModulithPublicationProcessingFailedEvent.class);
    }

    @Override
    protected void consume(ModulithPublicationProcessingFailedEvent errorEvent) {
        errorEventHandler.handle(clusterName, errorEvent);
    }
}
