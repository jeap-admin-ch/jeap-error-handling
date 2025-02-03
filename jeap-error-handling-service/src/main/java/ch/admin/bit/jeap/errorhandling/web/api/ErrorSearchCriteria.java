package ch.admin.bit.jeap.errorhandling.web.api;


import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Builder
public class ErrorSearchCriteria {

    public static final Integer PAGE_DEFAULT_VALUE = 0;
    public static final Integer SIZE_DEFAULT_VALUE = 10;

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

    @JsonIgnore
    public Pageable getPageable() {
        return PageRequest.of(
                ObjectUtils.firstNonNull(this.pageIndex, PAGE_DEFAULT_VALUE),
                ObjectUtils.firstNonNull(this.pageSize, SIZE_DEFAULT_VALUE),
                Sort.by(getOrders()));
    }

    private List<Sort.Order> getOrders() {
        List<Sort.Order> orders = new ArrayList<>();
        if (hasMultipleColumns()) {
            for (String sortOrder : this.sort) {
                String[] propertyDirection = sortOrder.split(",");
                Assert.isTrue(hasPropertyAndDirection(propertyDirection), String.format("Invalid sort value '%s' ", Arrays.toString(propertyDirection)));
                orders.add(new Sort.Order(getDirection(propertyDirection[1]), propertyDirection[0]));
            }
        } else {
            Assert.isTrue(hasPropertyAndDirection(this.sort), String.format("Invalid sort value '%s' ", Arrays.toString(this.sort)));
            orders.add(new Sort.Order(getDirection(this.sort[1]), this.sort[0]));
        }
        return orders;
    }

    private boolean hasPropertyAndDirection(String[] array) {
        return (array.length == 2);
    }

    private boolean hasMultipleColumns() {
        return this.sort[0].contains(",");
    }

    private Sort.Direction getDirection(String direction) {
        return Sort.Direction.fromString(direction);
    }
}
