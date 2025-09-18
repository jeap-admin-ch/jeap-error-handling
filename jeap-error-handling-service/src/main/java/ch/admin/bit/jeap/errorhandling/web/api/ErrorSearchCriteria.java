package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import lombok.Builder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Builder
public class ErrorSearchCriteria extends BaseSearchCriteria {

    private ZonedDateTime from;
    private ZonedDateTime to;
    private String eventName;
    private String traceId;
    private String eventId;
    private String serviceName;
    private List<Error.ErrorState> states;
    private String errorCode;
    private Integer pageIndex;
    private Integer pageSize;
    private String[] sort;
    private Pattern stacktracePattern;
    private String closingReason;
    private String ticketNumber;

    public Optional<ZonedDateTime> getFrom() {
        return Optional.ofNullable(this.from);
    }
    public Optional<ZonedDateTime> getTo() {
        return Optional.ofNullable(this.to);
    }
    public Optional<String> getEventName() {
        return Optional.ofNullable(this.eventName);
    }
    public Optional<String> getTraceId() {
        return Optional.ofNullable(this.traceId);
    }
    public Optional<String> getEventId() {
        return Optional.ofNullable(this.eventId);
    }
    public Optional<String> getServiceName() {
        return Optional.ofNullable(this.serviceName);
    }
    public Optional<List<Error.ErrorState>> getStates() {
        return Optional.ofNullable(this.states);
    }
    public Optional<String> getErrorCode() {
        return Optional.ofNullable(this.errorCode);
    }
    public Optional<Pattern> getStacktrace() {
        return Optional.ofNullable(this.stacktracePattern);
    }
    public Optional<String> getClosingReason() {
        return Optional.ofNullable(this.closingReason);
    }
    public Optional<String> getTicketNumber() {
        return Optional.ofNullable(this.ticketNumber);
    }

    @Override
    protected Integer getPageIndex() { return pageIndex; }
    @Override
    protected Integer getPageSize() {return pageSize; }
    @Override
    protected String[] getSort() { return sort;}

}
