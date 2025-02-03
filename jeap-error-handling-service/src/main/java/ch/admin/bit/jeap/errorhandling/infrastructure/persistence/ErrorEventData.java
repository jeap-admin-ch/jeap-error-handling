package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;


import lombok.*;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // for Builder
@NoArgsConstructor // for JPA
@ToString
@Embeddable
public class ErrorEventData {

    @NonNull
    private String code;
    @NonNull
    @Enumerated(EnumType.STRING)
    private Temporality temporality;
    @NonNull
    private String message;
    private String description;
    @ToString.Exclude
    private String stackTrace;
    private String stackTraceHash;

    public enum Temporality {TEMPORARY, PERMANENT, UNKNOWN}


}
