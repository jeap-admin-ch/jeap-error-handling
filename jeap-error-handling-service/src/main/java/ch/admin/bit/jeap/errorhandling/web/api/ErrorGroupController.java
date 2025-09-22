package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.domain.exceptions.InvalidUuidException;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedData;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedDataList;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static ch.admin.bit.jeap.errorhandling.web.api.DateTimeUtils.parseDate;
import static ch.admin.bit.jeap.errorhandling.web.api.DateTimeUtils.timestamp;

@Tag(name = "ErrorGroup")
@RestController
@RequestMapping("/api/error-group")
@Slf4j
@RequiredArgsConstructor
public class ErrorGroupController {

    private final ErrorGroupService errorGroupService;

    @PostMapping()
    @PreAuthorize("hasRole('errorgroup','view')")
    public ErrorGroupResponse getGroups(@RequestParam(name = "pageIndex", required = false, defaultValue = "0") int pageIndex,
                                        @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize,
                                        @RequestBody(required = false) ErrorGroupSearchFormDto errorGroupSearchFormDto) {

        if (errorGroupSearchFormDto == null) {
            errorGroupSearchFormDto = new ErrorGroupSearchFormDto();
        }

        ErrorGroupSearchCriteria errorGroupSearchCriteria = ErrorGroupSearchCriteria.builder()
                .dateFrom(parseDate(errorGroupSearchFormDto.getDateFrom()))
                .dateTo(parseDate(errorGroupSearchFormDto.getDateTo()))
                .noTicket(errorGroupSearchFormDto.getNoTicket())
                .source(errorGroupSearchFormDto.getSource())
                .messageType(errorGroupSearchFormDto.getMessageType())
                .errorCode(errorGroupSearchFormDto.getErrorCode())
                .jiraTicket(errorGroupSearchFormDto.getJiraTicket())
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .build();

        ErrorGroupAggregatedDataList errorGroups = errorGroupService.findErrorGroupAggregatedData(errorGroupSearchCriteria);
        List<ErrorGroupDTO> errorGroupDTOS = errorGroups.groups().stream()
                .map(this::mapToDTO)
                .toList();
        return new ErrorGroupResponse(errorGroups.totalElements(), errorGroupDTOS);
    }

    @GetMapping("/{errorGroupId}")
    @PreAuthorize("hasRole('errorgroup','view')")
    public ResponseEntity<ErrorGroupDTO> getErrorGroupById(@PathVariable String errorGroupId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(errorGroupId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        ErrorGroupAggregatedData data = errorGroupService.getErrorGroupAggregatedData(uuid);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        ErrorGroupDTO dto = mapToDTO(data);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/update-ticket-number")
    @PreAuthorize("hasRole('errorgroup','edit')")
    public ResponseEntity<ErrorGroupDTO> updateTicketNumber(@RequestBody @Valid UpdateTicketNumberRequest ticketNumberRequest) {
        UUID groupId = parseGroupId(ticketNumberRequest.getErrorGroupId());
        errorGroupService.updateTicketNumber(groupId, ticketNumberRequest.getTicketNumber());
        ErrorGroupDTO errorGroupDTO = getErrorGroupDTO(groupId);
        return ResponseEntity.ok(errorGroupDTO);
    }

    @PostMapping("/{groupId}/issue")
    @PreAuthorize("hasRole('errorgroup','edit')")
    public ResponseEntity<ErrorGroupDTO> createIssue(@PathVariable(name = "groupId") UUID groupId) {
        try {
            errorGroupService.createIssue(groupId);
        } catch (Throwable t) {
            log.error("Issue creation failed: '{}'", t.getMessage(), t);
            throw t;
        }
        ErrorGroupDTO errorGroupDTO = getErrorGroupDTO(groupId);
        return ResponseEntity.ok(errorGroupDTO);
    }

    @PostMapping("/update-free-text")
    @PreAuthorize("hasRole('errorgroup','edit')")
    public ResponseEntity<ErrorGroupDTO> updateFreeText(@RequestBody @Valid UpdateFreeTextRequest freeTextRequest) {
        UUID groupId = parseGroupId(freeTextRequest.getErrorGroupId());
        errorGroupService.updateFreeText(groupId, freeTextRequest.getFreeText());
        ErrorGroupDTO errorGroupDTO = getErrorGroupDTO(groupId);
        return ResponseEntity.ok(errorGroupDTO);
    }

    private ErrorGroupDTO getErrorGroupDTO(UUID groupId) {
        ErrorGroupAggregatedData errorGroupAggregatedData = errorGroupService.getErrorGroupAggregatedData(groupId);
        return mapToDTO(errorGroupAggregatedData);
    }

    private UUID parseGroupId(String groupIdString) {
        try {
            return UUID.fromString(groupIdString);
        } catch (IllegalArgumentException e) {
            throw new InvalidUuidException(groupIdString);
        }
    }

    private ErrorGroupDTO mapToDTO(ErrorGroupAggregatedData groupAggregatedData) {
        return new ErrorGroupDTO(
                groupAggregatedData.getGroupId().toString(),
                groupAggregatedData.getErrorCount(),
                groupAggregatedData.getErrorEvent(),
                groupAggregatedData.getErrorPublisher(),
                groupAggregatedData.getErrorCode(),
                groupAggregatedData.getErrorMessage(),
                timestamp(groupAggregatedData.getFirstErrorAt()),
                timestamp(groupAggregatedData.getLatestErrorAt()),
                groupAggregatedData.getTicketNumber(),
                groupAggregatedData.getFreeText(),
                groupAggregatedData.getStackTraceHash()
        );
    }
}
