package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorGroupListSearchFormDto {

    private String dateFrom;
    private String dateTo;
    private String stacktracePattern;
    private String messagePattern;
    private String sortField;
    private String sortOrder;

    public Pattern getStacktracePattern() {
        return StringUtils.isBlank(stacktracePattern) ? null : Pattern.compile(stacktracePattern); //NOSONAR
    }

    public Pattern getMessagePattern() {
        return StringUtils.isBlank(messagePattern) ? null : Pattern.compile(messagePattern); //NOSONAR
    }

    public String getSortField() {
        return StringUtils.defaultIfBlank(sortField, "created");
    }

    public String getSortOrder() {
        return StringUtils.defaultIfBlank(sortOrder, "desc");
    }




}
