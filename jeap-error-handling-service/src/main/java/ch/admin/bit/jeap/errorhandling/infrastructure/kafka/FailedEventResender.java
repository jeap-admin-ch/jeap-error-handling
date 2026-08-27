package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.CausingEvent;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FailedEventResender {

    private final KafkaFailedEventResender kafkaResender;
    private final ModulithPublicationCommandSender modulithCommandSender;

    public void resend(Error error) {
        if (error.getCausingEvent().getOrigin() == CausingEvent.Origin.MODULITH_PUBLICATION) {
            modulithCommandSender.retry(error);
        } else {
            kafkaResender.resend(error);
        }
    }

    public void discardIfModulith(Error error, String reason) {
        if (error.getCausingEvent().getOrigin() == CausingEvent.Origin.MODULITH_PUBLICATION) {
            modulithCommandSender.discard(error, reason);
        }
    }
}
