package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor // for JPA
@ToString
@Entity
public class ScheduledResend {

    @Id
    private UUID id = UUID.randomUUID();
    private ZonedDateTime resendAt;
    private UUID errorId;
    private boolean cancelled;
    @Setter
    private ZonedDateTime resentAt;
    @Version
    private int version;

    public ScheduledResend(UUID errorId, ZonedDateTime resendAt) {
        if (errorId == null) {
            throw new IllegalArgumentException("errorId must be provided");
        }
        if (resendAt == null) {
            throw new IllegalArgumentException("resendAt must be provided");
        }
        this.errorId = errorId;
        this.resendAt = resendAt;
    }

    public void cancel() {
        cancelled = true;
    }
}
