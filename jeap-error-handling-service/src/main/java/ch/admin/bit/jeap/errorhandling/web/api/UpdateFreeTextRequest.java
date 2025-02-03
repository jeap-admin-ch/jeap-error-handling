package ch.admin.bit.jeap.errorhandling.web.api;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateFreeTextRequest {
    @NotNull(message = "ErrorGroupId number cannot be null")
    private String errorGroupId;
    @NotNull(message = "Free text cannot be null")
    private String freeText;
}
