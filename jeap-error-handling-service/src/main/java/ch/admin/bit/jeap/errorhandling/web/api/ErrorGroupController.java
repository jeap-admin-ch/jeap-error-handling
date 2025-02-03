package ch.admin.bit.jeap.errorhandling.web.api;

import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedData;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupAggregatedDataList;
import ch.admin.bit.jeap.errorhandling.domain.group.ErrorGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static ch.admin.bit.jeap.errorhandling.web.api.DateTimeUtils.timestamp;

@Tag(name = "ErrorGroup")
@RestController
@RequestMapping("/api/error-group")
@Slf4j
@RequiredArgsConstructor
public class ErrorGroupController {

    private final ErrorGroupService errorGroupService;

    @GetMapping
    @PreAuthorize("hasRole('errorgroup','view')")
    public ErrorGroupResponse getGroups(@RequestParam(name = "pageIndex", required = false, defaultValue = "0") int pageIndex,
                                        @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {
        ErrorGroupAggregatedDataList errorGroups = errorGroupService.findErrorGroupAggregatedData(PageRequest.of(pageIndex, pageSize));
        List<ErrorGroupDTO> errorGroupDTOS = errorGroups.groups().stream()
                .map(this::mapToDTO)
                .toList();
        return new ErrorGroupResponse(errorGroups.totalElements(), errorGroupDTOS);
    }

    @PostMapping("/update-ticket-number")
    @PreAuthorize("hasRole('errorgroup','edit')")
    public ResponseEntity<ErrorGroupDTO> updateTicketNumber(@RequestBody @Valid UpdateTicketNumberRequest ticketNumberRequest) {
        // convert the String UUID from the request to UUID type
        UUID groupId;
        try {
            groupId = UUID.fromString(ticketNumberRequest.getErrorGroupId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        errorGroupService.updateTicketNumber(groupId, ticketNumberRequest.getTicketNumber());
        ErrorGroupAggregatedData errorGroupAggregatedData = errorGroupService.getErrorGroupAggregatedData(groupId);
        ErrorGroupDTO errorGroupDTO = mapToDTO(errorGroupAggregatedData);
        return ResponseEntity.ok(errorGroupDTO);
    }

    @PostMapping("/update-free-text")
    @PreAuthorize("hasRole('errorgroup','edit')")
    public ResponseEntity<ErrorGroupDTO> updateFreeText(@RequestBody @Valid UpdateFreeTextRequest freeTextRequest) {
        // convert the String UUID from the request to UUID type
        UUID groupId;
        try {
            groupId = UUID.fromString(freeTextRequest.getErrorGroupId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        errorGroupService.updateFreeText(groupId, freeTextRequest.getFreeText());
        ErrorGroupAggregatedData errorGroupAggregatedData = errorGroupService.getErrorGroupAggregatedData(groupId);
        ErrorGroupDTO errorGroupDTO = mapToDTO(errorGroupAggregatedData);
        return ResponseEntity.ok(errorGroupDTO);
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
                groupAggregatedData.getFreeText()
        );
    }
}
