package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import lombok.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MessageHeader {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_header_id_gen")
    @SequenceGenerator(name = "message_header_id_gen", sequenceName = "message_header_seq", allocationSize = 1)
    private Long id;

    @NotNull
    private String headerName;

    @NotNull
    private byte[] headerValue;
}
