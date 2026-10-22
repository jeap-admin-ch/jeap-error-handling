package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor // for Jackson
@AllArgsConstructor
public class DeadLetterCountDTO {
    private long pendingMessageCount;
}
