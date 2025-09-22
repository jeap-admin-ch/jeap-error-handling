package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ErrorGroupConfigurationDTO {
    private String ticketingSystemUrl;
    private boolean issueTrackingEnabled;
}
