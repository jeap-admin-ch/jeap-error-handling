package ch.admin.bit.jeap.errorhandling.web.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorSearchFormDto {

    private String dateFrom;
    private String dateTo;

    @Schema(description = "Name of the Origin Event")
    private String eventName;

    private String traceId;

    private String eventId;

    private String eventSource;

    private List<String> states;

    private String errorCode;

    private String sortField;

    private String sortOrder;

    private String stacktracePattern;

    private String closingReason;

    private String ticketNumber;

    public String getEventName() {
        return StringUtils.defaultIfBlank(eventName, null);
    }

    public String getTraceId() {
        return StringUtils.defaultIfBlank(traceId, null);
    }

    public String getEventId() {
        return StringUtils.defaultIfBlank(eventId, null);
    }

    public String getClosingReason() {
        return StringUtils.defaultIfBlank(closingReason, null);
    }

    public String getSortField() {
        return StringUtils.defaultIfBlank(sortField, "created");
    }

    public String getSortOrder() {
        return StringUtils.defaultIfBlank(sortOrder, "desc");
    }

    public Pattern getStacktracePattern() {
        return StringUtils.isBlank(stacktracePattern) ? null : Pattern.compile(stacktracePattern); //NOSONAR
    }

    public String getTicketNumber() {
        return StringUtils.defaultIfBlank(ticketNumber, null);
    }

}
