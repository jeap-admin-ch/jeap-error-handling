package ch.admin.bit.jeap.errorhandling.domain.group;

import java.util.List;

public record ErrorGroupAggregatedDataList(long totalElements, List<ErrorGroupAggregatedData> groups) {
    public ErrorGroupAggregatedDataList {
        groups = List.copyOf(groups);
    }
}