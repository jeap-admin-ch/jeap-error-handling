package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import lombok.*;

import jakarta.persistence.Embeddable;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE) // for Builder
@NoArgsConstructor // for JPA
@ToString
@Embeddable
public class EventType {
    @NonNull
    private String name;

    @NonNull
    private String version;

}
