package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor // for Jackson
@AllArgsConstructor
public class DeleteErrorsResultDTO {
    private int totalItems;
    private int totalErrors;
}
