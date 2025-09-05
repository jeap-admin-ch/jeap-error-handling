package ch.admin.bit.jeap.errorhandling.web.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Optional;

@Builder
public class ErrorGroupSearchCriteria {

    public static final Integer PAGE_DEFAULT_VALUE = 0;
    public static final Integer SIZE_DEFAULT_VALUE = 10;

    private ZonedDateTime dateFrom;
    private ZonedDateTime dateTo;
    private Boolean noTicket;
    private String source;
    private String messageType;
    private String errorCode;
    private String jiraTicket;

    private Integer pageIndex;
    private Integer pageSize;

    public Optional<ZonedDateTime> getDateFrom() {
        return Optional.ofNullable(this.dateFrom);
    }
    public Optional<ZonedDateTime> getDateTo() {
        return Optional.ofNullable(this.dateTo);
    }
    public Optional<Boolean> getNoTicket() { return Optional.ofNullable(this.noTicket); }
    public Optional<String> getSource() { return Optional.ofNullable(this.source); }
    public Optional<String> getMessageType() { return Optional.ofNullable(this.messageType); }
    public Optional<String> getErrorCode() { return Optional.ofNullable(this.errorCode); }
    public Optional<String> getJiraTicket() { return Optional.ofNullable(this.jiraTicket); }

    @JsonIgnore
    public Pageable getPageable() {
        return PageRequest.of(
                ObjectUtils.firstNonNull(this.pageIndex, PAGE_DEFAULT_VALUE),
                ObjectUtils.firstNonNull(this.pageSize, SIZE_DEFAULT_VALUE));
    }
}
