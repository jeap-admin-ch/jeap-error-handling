package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.domain.audit.AuditLogService;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorList;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorSearchService;
import ch.admin.bit.jeap.errorhandling.domain.error.ErrorService;
import ch.admin.bit.jeap.errorhandling.domain.resend.scheduler.ScheduledResendService;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.DomainEventDeserializer;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.AuditLog;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.Error;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.EventMessage;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.MessageHeader;
import ch.admin.bit.jeap.errorhandling.infrastructure.persistence.User;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.signature.SignatureHeaders;
import ch.admin.bit.jeap.security.resource.semanticAuthentication.ServletSemanticAuthorization;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Tag(name = "Errors")
@RestController
@RequestMapping("/api/error")
@Slf4j
public class ErrorController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_OVERVIEW_MESSAGE_LENGTH = 50;

    private final ErrorService errorService;
    private final ErrorSearchService errorSearchService;
    private final ScheduledResendService scheduledResendService;
    private final AuditLogService auditLogService;
    private final DomainEventDeserializer domainEventDeserializer;
    private final ServletSemanticAuthorization jeapSemanticAuthorization;
    private final String defaultClusterName;

    public ErrorController(ErrorService errorService, ErrorSearchService errorSearchService,
                           ScheduledResendService scheduledResendService, AuditLogService auditLogService,
                           DomainEventDeserializer domainEventDeserializer,
                           ServletSemanticAuthorization jeapSemanticAuthorization, KafkaProperties kafkaProperties) {
        this.errorService = errorService;
        this.errorSearchService = errorSearchService;
        this.scheduledResendService = scheduledResendService;
        this.auditLogService = auditLogService;
        this.domainEventDeserializer = domainEventDeserializer;
        this.jeapSemanticAuthorization = jeapSemanticAuthorization;
        this.defaultClusterName = kafkaProperties.getDefaultClusterName();
    }

    private static String longStringEllipis(String message) {
        return StringUtils.abbreviate(message, MAX_OVERVIEW_MESSAGE_LENGTH);
    }

    private static String timestamp(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    @PostMapping("/")
    @PreAuthorize("hasRole('error','view')")
    @Schema(description = "Find Errors by Filter")
    @Transactional(readOnly = true)
    public ErrorListDTO findErrors(
            @RequestParam(name = "pageIndex", required = false, defaultValue = "0") int pageIndex,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize,
            @RequestBody ErrorSearchFormDto errorSearchFormDto) {

        String[] sort = {errorSearchFormDto.getSortField(), errorSearchFormDto.getSortOrder()};
        ErrorSearchCriteria errorSearchCriteria = ErrorSearchCriteria.builder()
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .from(parseDate(errorSearchFormDto.getDateFrom()))
                .to(parseDate(errorSearchFormDto.getDateTo()))
                .eventName(errorSearchFormDto.getEventName())
                .traceId(errorSearchFormDto.getTraceId())
                .eventId(errorSearchFormDto.getEventId())
                .serviceName(errorSearchFormDto.getEventSource())
                .states(this.convertErrorStates(errorSearchFormDto.getStates()))
                .errorCode(errorSearchFormDto.getErrorCode())
                .stacktracePattern(errorSearchFormDto.getStacktracePattern())
                .closingReason(errorSearchFormDto.getClosingReason())
                .ticketNumber(errorSearchFormDto.getTicketNumber())
                .sort(sort)
                .build();

        ErrorList errorList = errorSearchService.search(errorSearchCriteria);
        return buildErrorList(errorList);
    }

    @GetMapping("/eventsources")
    @PreAuthorize("hasRole('error','view')")
    @Schema(description = "Get a List of all EventSources")
    @Transactional(readOnly = true)
    public List<String> getAllEventSources() {
        return errorSearchService.getAllEventSources();
    }

    @GetMapping("/errorcodes")
    @PreAuthorize("hasRole('error','view')")
    @Schema(description = "Get a List of all ErrorCodes")
    @Transactional(readOnly = true)
    public List<String> getAllErrorCodes() {
        return errorSearchService.getAllErrorCodes();
    }

    @GetMapping("/eventnames")
    @PreAuthorize("hasRole('error','view')")
    @Schema(description = "Get a list of all distinct event names")
    @Transactional(readOnly = true)
    public List<String> getAllEventNames() {
        return errorSearchService.getAllEventNames();
    }

    @Schema(description = "Returns a paged list of all permanent errors")
    @GetMapping("/permanent")
    @PreAuthorize("hasRole('error','view')")
    @Transactional(readOnly = true)
    public ErrorListDTO listPermanentErrors(
            @RequestParam(name = "pageIndex", required = false, defaultValue = "0") int pageIndex,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {

        ErrorList errorList = errorService.getPermanentErrorList(pageIndex, pageSize);

        return buildErrorList(errorList);
    }

    @Schema(description = "Returns a paged list of all temporary errors")
    @GetMapping("/temporary")
    @PreAuthorize("hasRole('error','view')")
    @Transactional(readOnly = true)
    public ErrorListDTO listTemporaryErrors(
            @RequestParam(name = "pageIndex", required = false, defaultValue = "0") int pageIndex,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {

        ErrorList errorList = errorService.getTemporaryErrorList(pageIndex, pageSize);

        return buildErrorList(errorList);
    }

    private ErrorListDTO buildErrorList(ErrorList errorList) {
        return ErrorListDTO.builder()
                .totalErrorCount(errorList.getTotalElements())
                .errors(toErrorDtos(errorList.getErrors()))
                .build();
    }

    @Schema(description = "Returns details about an error")
    @GetMapping("/{errorId}/details")
    @PreAuthorize("hasRole('error','view')")
    @Transactional(readOnly = true)
    public ErrorDTO getErrorDetails(@PathVariable("errorId") UUID errorId) {
        Error error = errorService.getError(errorId);

        boolean userCanRetry = jeapSemanticAuthorization.hasRole("error", "retry");
        boolean userCanDelete = jeapSemanticAuthorization.hasRole("error", "delete");

        return toErrorDtoWithDetails(error, userCanRetry, userCanDelete);
    }

    @Schema(description = "Returns the payload of a causing event of an error, as JSON-formatted string")
    @GetMapping(value = "/{errorId}/event/payload", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasRole('error','view')")
    @Transactional(readOnly = true)
    public String getCausingEventPayload(@PathVariable("errorId") UUID errorId) {
        Error error = errorService.getError(errorId);
        String clusterName = error.getCausingEventMessage().getClusterNameOrDefault(defaultClusterName);
        EventMessage causingEventMessage = error.getCausingEventMessage();
        try {
            return domainEventDeserializer.toJsonString(clusterName,
                    causingEventMessage.getTopic(),
                    causingEventMessage.getPayload());
        } catch (Exception deserializationFailed) {
            return """
                    {
                        "error": "Deserialization failed - payload is either not avro, or might be encrypted",
                        "message": "%s"
                    }
                    """.formatted(deserializationFailed.getMessage());
        }
    }

    @Schema(description = "Republishes the event which caused an error back to the original consumer of the event")
    @PostMapping(value = "/{errorId}/event/retry")
    @PreAuthorize("hasRole('error','retry')")
    public void retryEvent(@PathVariable("errorId") UUID errorId) {
        errorService.manualResend(errorId);
    }

    @Schema(description = "Republishes a list of events which caused an error back to the original consumer of the events")
    @PostMapping(value = "/event/retry")
    @PreAuthorize("hasRole('error','retry')")
    public void retryEventList(@RequestBody ErrorListDTO errors) {
        errors.getErrors().forEach(e -> errorService.manualResend(UUID.fromString(e.getId())));
    }


    @Schema(description = "Deletes the event")
    @DeleteMapping("/{errorId}")
    @PreAuthorize("hasRole('error','delete')")
    public void deleteError(
            @PathVariable("errorId") UUID errorId, @RequestParam(required = false) String reason) {
        errorService.delete(errorId, reason);
    }

    @Schema(description = "Deletes a list of events")
    @PostMapping("/delete")
    @PreAuthorize("hasRole('error','delete')")
    public void deleteErrorList(@RequestBody ErrorListDTO errors, @RequestParam(required = false) String reason) {
        errors.getErrors().forEach(e -> errorService.delete(UUID.fromString(e.getId()), reason));
    }

    private List<ErrorDTO> toErrorDtos(List<Error> errors) {
        return errors.stream()
                .map(this::toErrorDto)
                .collect(toList());
    }

    private ErrorDTO toErrorDto(Error error) {
        return toErrorDtoBuilder(error).build();
    }

    private ErrorDTO toErrorDtoWithDetails(Error error, boolean userCanRetry, boolean userCanDelete) {
        boolean canRetry = error.getState().isRetryAllowed() && userCanRetry;
        boolean canDelete = error.getState().isDeleteAllowed() && userCanDelete;
        int errorCountForEvent = errorService.getErrorCountForCausingEvent(error.getCausingEventMetadata().getId());
        return toErrorDtoBuilder(error)
                .canRetry(canRetry)
                .canDelete(canDelete)
                .errorCountForEvent(errorCountForEvent)
                .errorTemporality(error.getErrorEventData().getTemporality().name())
                .stacktrace(error.getErrorEventData().getStackTrace())
                .eventTopicDetails(topicDetails(error.getCausingEventMessage()))
                .eventClusterName(error.getCausingEventMessage().getClusterNameOrDefault(defaultClusterName))
                .auditLogDTOs(getAuditLogDtos(error))
                .build();
    }

    private List<AuditLogDTO> getAuditLogDtos(Error error) {
        return auditLogService.getAuditLogs(error.getId()).stream()
                .map(this::toAuditLogDto)
                .collect(Collectors.toList());
    }

    private AuditLogDTO toAuditLogDto(AuditLog auditLog) {
        final User user = auditLog.getUser();
        return AuditLogDTO.builder()
                .authContext(user.getAuthContext())
                .subject(user.getSubject())
                .action(auditLog.getAction())
                .created(timestamp(auditLog.getCreated()))
                .extId(user.getExtId())
                .familyName(user.getFamilyName())
                .givenName(user.getGivenName())
                .build();
    }

    private String topicDetails(EventMessage eventMessage) {
        return eventMessage.getTopic() +
                ", Partition " + eventMessage.getPartition() +
                ", Offset " + eventMessage.getOffset();
    }

    private ErrorDTO.ErrorDTOBuilder toErrorDtoBuilder(Error error) {
        ZonedDateTime nextResendTime = scheduledResendService.getNextResendTimestamp(error.getId());
        String jeapCert = extractJeapCert(error.getCausingEvent().getHeaders());
        return ErrorDTO.builder()
                .id(error.getId().toString())
                .errorState(error.getState().name())
                .timestamp(timestamp(error.getErrorEventMetadata().getCreated()))
                .errorMessage(error.getErrorEventData().getMessage())
                .errorCode(longStringEllipis(error.getErrorEventData().getCode()))
                .errorPublisher(error.getErrorEventMetadata().getPublisher().getService())
                .nextResendTimestamp(timestamp(nextResendTime))
                .eventName(error.getCausingEventMetadata().getType().getName())
                .eventId(error.getCausingEventMetadata().getId())
                .eventTimestamp(timestamp(error.getCausingEventMetadata().getCreated()))
                .eventPublisher(error.getCausingEventMetadata().getPublisher().getService())
                .originalTraceIdString(error.getOriginalTraceContext() != null ? error.getOriginalTraceContext().getTraceIdString() : null)
                .closingReason(error.getClosingReason())
                .ticketNumber(error.getErrorGroup() != null ? error.getErrorGroup().getTicketNumber() : null)
                .freeText(error.getErrorGroup() != null ? error.getErrorGroup().getFreeText() : null)
                .signed(jeapCert != null)
                .jeapCert(jeapCert)
                .canRetry(error.getState().isRetryAllowed())
                .canDelete(error.getState().isDeleteAllowed());
    }

    private String extractJeapCert(List<MessageHeader> headers) {
        return extractHeaderValue(SignatureHeaders.SIGNATURE_CERTIFICATE_HEADER_KEY, headers);
    }

    private String extractHeaderValue(String headerName, List<MessageHeader> headers) {
        if (headers == null) {
            return null;
        }
        return headers.stream()
                .filter(header -> header.getHeaderName().equals(headerName))
                .map(MessageHeader::getHeaderValue)
                .map(this::bytesToHex)
                .findFirst()
                .orElse(null);
    }

    private String bytesToHex(byte[] bytes) {
        return HexFormat.of().withDelimiter(" ").formatHex(bytes).toUpperCase();
    }

    private List<Error.ErrorState> convertErrorStates(List<String> states) {
        if (states == null) return null;
        return states.stream().map(Error.ErrorState::valueOf).toList();
    }

    private ZonedDateTime parseDate(String date) {
        return StringUtils.isBlank(date) ? null : ZonedDateTime.parse(date);
    }
}
