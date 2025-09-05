package ch.admin.bit.jeap.errorhandling.web.api;

public record ErrorGroupDTO(
        String errorGroupId,
        Long errorCount,
        String errorEvent,
        String errorPublisher,
        String errorCode,
        String errorMessage,
        String firstErrorAt,
        String latestErrorAt,
        String ticketNumber,
        String freeText,
        String stackTraceHash
) {
}
