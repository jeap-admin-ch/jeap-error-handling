package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
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
    private EventMessage message;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "causing_event_id", referencedColumnName = "id")
    private List<MessageHeader> headers;

    public void update(EventMetadata eventMetadata, EventMessage eventMessage, List<MessageHeader> headers) {
        this.metadata = eventMetadata;
        this.message = eventMessage;
        clearHeaders();
        if (headers != null) {
            this.headers.addAll(headers);
        }
    }

    private void clearHeaders() {
        if (this.headers == null) {
            this.headers = new ArrayList<>(); // Hibernate requires a mutable collection
        } else {
            this.headers.clear(); // Make sure to re-use collection provided by Hibernate
        }
    }
}
