package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor // for Jackson
@AllArgsConstructor
public class ErrorListDTO {
    private long totalErrorCount;
    private List<ErrorDTO> errors;
}
