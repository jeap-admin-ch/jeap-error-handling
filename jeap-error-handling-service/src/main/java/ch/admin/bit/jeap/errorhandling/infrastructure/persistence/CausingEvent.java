package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE) // for Builder
@NoArgsConstructor // for JPA
@ToString
@Entity
public class CausingEvent {

    @Id
    @Builder.Default
    @NonNull
    private UUID id = UUID.randomUUID();

    @Embedded
    @NonNull
    private EventMetadata metadata;

    @Embedded
    @NonNull
    @Setter
    private EventMessage message;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "causing_event_id", referencedColumnName = "id")
    private List<MessageHeader> headers;
}
