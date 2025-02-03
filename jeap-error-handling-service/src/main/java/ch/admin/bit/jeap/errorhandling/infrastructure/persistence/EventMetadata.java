package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import lombok.*;

import jakarta.persistence.Embeddable;
import java.time.ZonedDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE) // for Builder
@NoArgsConstructor // for JPA
@ToString
@Embeddable
public class EventMetadata {

    @NonNull
    private String id;

    @NonNull
    private String idempotenceId;

    @NonNull
    private ZonedDateTime created;

    @NonNull
    private EventType type;

    @NonNull
    private EventPublisher publisher;

}
