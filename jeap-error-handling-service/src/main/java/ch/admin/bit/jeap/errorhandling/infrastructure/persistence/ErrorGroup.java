package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
@ToString
@Entity
@EqualsAndHashCode
public class ErrorGroup {

    @Id
    @NonNull
    private UUID id;
    @NonNull
    private String errorCode;
    @NonNull
    private String eventName;
    @NonNull
    private String errorPublisher;
    @NonNull
    private String errorMessage;
    private String errorStackTraceHash;
    private String ticketNumber;
    private String freeText;
    @NonNull
    private ZonedDateTime created;
    private ZonedDateTime modified;

    public ErrorGroup(@NonNull String errorCode,
                      @NonNull String eventName,
                      @NonNull String errorPublisher,
                      @NonNull String errorMessage,
                      @NonNull String errorStackTraceHash) {
        this.id = UUID.randomUUID();
        this.created = ZonedDateTime.now();
        this.errorCode = errorCode;
        this.eventName = eventName;
        this.errorPublisher = errorPublisher;
        this.errorMessage = errorMessage;
        this.errorStackTraceHash = errorStackTraceHash;

    }

    public static ErrorGroup from(Error error) {
        return new ErrorGroup(
                error.getErrorEventData().getCode(),
                error.getCausingEventMetadata().getType().getName(),
                error.getErrorEventMetadata().getPublisher().getService(),
                error.getErrorEventData().getMessage(),
                error.getErrorEventData().getStackTraceHash()
        );
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
        modifiedNow();
    }

    public void setFreeText(String freeText) {
        this.freeText = freeText;
        modifiedNow();
    }

    private void modifiedNow() {
        modified = ZonedDateTime.now();
    }
}
