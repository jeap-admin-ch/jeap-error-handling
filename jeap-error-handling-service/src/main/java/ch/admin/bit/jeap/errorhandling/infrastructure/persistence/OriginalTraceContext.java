package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import jakarta.persistence.Embeddable;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // for Builder
@NoArgsConstructor // for JPA
@ToString
@Embeddable
public class OriginalTraceContext {

    private Long traceIdHigh;

    private Long traceId;

    private Long spanId;

    private Long parentSpanId;

    private String traceIdString;

}
