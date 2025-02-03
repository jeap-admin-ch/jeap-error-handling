package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE) // for Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Entity
public class AuditLog {

    @Id
    @Builder.Default
    @EqualsAndHashCode.Include
    @NonNull
    private UUID id = UUID.randomUUID();

    @Embedded
    @NonNull
    private User user;

    @NonNull
    private UUID errorId;

    @NonNull
    @Enumerated(EnumType.STRING)
    private AuditedAction action;

    @NonNull
    private ZonedDateTime created;

    public enum AuditedAction {RESEND_CAUSING_EVENT, DELETE_ERROR}

}
