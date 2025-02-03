package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import lombok.*;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
@ToString
@Entity
public class Error {
    @Id
    @Builder.Default
    @NonNull
    private UUID id = UUID.randomUUID();
    @NonNull
    @Enumerated(EnumType.STRING)
    private ErrorState state;
    @Embedded
    @NonNull
    private ErrorEventData errorEventData;
    @Embedded
    @NonNull
    private EventMetadata errorEventMetadata;
    @ManyToOne
    @NonNull
    private CausingEvent causingEvent;
    @NonNull
    private ZonedDateTime created;
    private ZonedDateTime modified;
    @Version
    private int version;
    private UUID manualTaskId;
    @Embedded
    private OriginalTraceContext originalTraceContext;
    private String closingReason;
    @ManyToOne(fetch = FetchType.LAZY)
    private ErrorGroup errorGroup;

    public void setErrorGroup(ErrorGroup errorGroup) {
        this.errorGroup = errorGroup;
        modifiedNow();
    }

    public void setState(ErrorState state) {
        this.state = state;
        modifiedNow();
    }

    public void setManualTaskId(UUID manualTaskId) {
        this.manualTaskId = manualTaskId;
        modifiedNow();
    }

    public void setClosingReason(String reason) {
        this.closingReason = reason;
        modifiedNow();
    }

    private void modifiedNow() {
        modified = ZonedDateTime.now();
    }

    public EventMessage getCausingEventMessage() {
        return causingEvent.getMessage();
    }

    public EventMetadata getCausingEventMetadata() {
        return causingEvent.getMetadata();
    }

    @Getter
    public enum ErrorState {
        TEMPORARY_RETRY_PENDING(true, true),
        SEND_TO_MANUALTASK(true, true),
        PERMANENT(true, true),
        TEMPORARY_RETRIED(),
        RESOLVE_ON_MANUALTASK(),
        PERMANENT_RETRIED(),
        DELETE_ON_MANUALTASK(false, true),
        DELETED(false, true);

        private final boolean deleteAllowed;
        private final boolean retryAllowed;

        ErrorState() {
            this(false, false);
        }

        ErrorState(boolean deleteAllowed, boolean retryAllowed) {
            this.deleteAllowed = deleteAllowed;
            this.retryAllowed = retryAllowed;
        }
    }
}
