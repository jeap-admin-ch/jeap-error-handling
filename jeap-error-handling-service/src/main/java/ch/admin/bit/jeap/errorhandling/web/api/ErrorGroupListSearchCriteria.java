package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.Builder;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Builder
public class ErrorGroupListSearchCriteria extends BaseSearchCriteria {

    private ZonedDateTime dateFrom;
    private ZonedDateTime dateTo;
    private Pattern stacktracePattern;
    private Pattern messagePattern;

    private Integer pageIndex;
    private Integer pageSize;
    private String[] sort;

    public Optional<ZonedDateTime> getDateFrom() {
        return Optional.ofNullable(this.dateFrom);
    }
    public Optional<ZonedDateTime> getDateTo() {
        return Optional.ofNullable(this.dateTo);
    }
    public Optional<Pattern> getStacktracePattern() {
        return Optional.ofNullable(this.stacktracePattern);
    }
    public Optional<Pattern> getMessagePattern() {
        return Optional.ofNullable(this.messagePattern);
    }

    @Override
    protected Integer getPageIndex() { return pageIndex; }
    @Override
    protected Integer getPageSize() {return pageSize; }
    @Override
    protected String[] getSort() { return sort;}

}
