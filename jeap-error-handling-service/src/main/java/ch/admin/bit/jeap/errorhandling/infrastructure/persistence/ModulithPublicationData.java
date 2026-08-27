package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
@ToString
@Embeddable
public class ModulithPublicationData {

    @NonNull
    @Column(name = "modulith_cluster_name")
    private String clusterName;

    @NonNull
    @Column(name = "modulith_publication_id")
    private String publicationId;

    @NonNull
    @Column(name = "modulith_listener")
    private String listener;

    @NonNull
    @Column(name = "modulith_event_type")
    private String eventType;

    @ToString.Exclude
    @Column(name = "modulith_serialized_event")
    private byte[] serializedEvent;

    @Column(name = "modulith_serialized_event_content_type")
    private String serializedEventContentType;

    @NonNull
    @Column(name = "modulith_retry_command_topic")
    private String retryCommandTopic;

    @NonNull
    @Column(name = "modulith_discard_command_topic")
    private String discardCommandTopic;
}
