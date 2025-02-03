package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import lombok.*;

import static org.springframework.util.StringUtils.hasText;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE) // for Builder
@NoArgsConstructor // for JPA
@ToString
@Embeddable
public class EventMessage {

    @NonNull
    @ToString.Exclude
    private byte[] payload;

    @ToString.Exclude
    private byte[] key;

    @NonNull
    private String topic;

    private String clusterName;

    private long partition;

    private long offset;

    public String getClusterNameOrDefault(String defaultClusterName) {
        return hasText(clusterName) ? clusterName : defaultClusterName;
    }
}
