package ch.admin.bit.jeap.errorhandling.domain.group;

import java.time.ZonedDateTime;
import java.util.UUID;

public interface ErrorGroupAggregatedData {
    UUID getGroupId();

    Long getErrorCount();

    String getErrorEvent();

    String getErrorPublisher();

    String getErrorCode();

    String getErrorMessage();

    ZonedDateTime getFirstErrorAt();

    ZonedDateTime getLatestErrorAt();

    String getTicketNumber();

    String getFreeText();

    String getStackTraceHash();
}
