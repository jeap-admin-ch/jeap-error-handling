package ch.admin.bit.jeap.errorhandling.web.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor // for Jackson
@AllArgsConstructor
public
class ErrorDTO {

    private String id;
    private String timestamp;
    private String errorState;
    private String errorMessage;
    private String errorCode;
    private String errorPublisher;
    private String errorTemporality;
    private String eventName;
    private String eventId;
    private String eventPublisher;
    private String eventTimestamp;
    private String eventTopicDetails;
    private String eventClusterName;
    private String stacktrace;
    private String nextResendTimestamp;
    private int errorCountForEvent;
    private boolean canRetry;
    private boolean canDelete;
    private String originalTraceIdString;
    private String closingReason;
    private List<AuditLogDTO> auditLogDTOs;
    private String ticketNumber;
    private String freeText;
    private boolean signed;
    private String jeapCert;
}
