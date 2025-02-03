package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE) // for Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode
@ToString
@Embeddable
public class User {

    @NonNull
    private String authContext;

    @NonNull
    private String subject;

    private String extId;
    private String givenName;
    private String familyName;

}
