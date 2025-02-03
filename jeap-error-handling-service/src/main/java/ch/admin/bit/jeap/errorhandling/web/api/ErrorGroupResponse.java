package ch.admin.bit.jeap.errorhandling.web.api;

import java.util.List;

public record ErrorGroupResponse(
        long totalErrorGroupCount,
        List<ErrorGroupDTO> groups
) {
    public ErrorGroupResponse {
        groups = List.copyOf(groups);
    }
}
