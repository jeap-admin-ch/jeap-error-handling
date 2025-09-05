package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorGroupSearchFormDto {

    private Boolean noTicket;
    private String dateFrom;
    private String dateTo;
    private String source;
    private String messageType;
    private String errorCode;
    private String jiraTicket;
}
