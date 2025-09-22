package ch.admin.bit.jeap.errorhandling.domain.group;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder( toBuilder = true)
public class ErrorGroupAggregatedDataRecord implements ErrorGroupAggregatedData {
    UUID groupId;
    ZonedDateTime groupCreatedAt;
    Long errorCount;
    String errorEvent;
    String errorPublisher;
    String errorCode;
    String errorMessage;
    ZonedDateTime firstErrorAt;
    ZonedDateTime latestErrorAt;
    String ticketNumber;
    String freeText;
    String stackTraceHash;
}
