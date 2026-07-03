package ch.admin.bit.jeap.errorhandling.domain.group;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class ErrorGroupSort {

    public static final String DEFAULT_SORT_FIELD = "latestErrorAt";
    public static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private static final Set<String> SUPPORTED_SORT_FIELDS = Set.of(
            "errorCount",
            "errorEvent",
            "errorPublisher",
            "errorCode",
            "stackTraceHash",
            "firstErrorAt",
            "latestErrorAt",
            "ticketNumber");

    private ErrorGroupSort() {
    }

    public static String validSortFieldOrDefault(String sortField, String defaultSortField) {
        if (isSupportedSortField(sortField)) {
            return sortField.trim();
        }
        if (isSupportedSortField(defaultSortField)) {
            return defaultSortField.trim();
        }
        return DEFAULT_SORT_FIELD;
    }

    public static Sort.Direction validSortDirectionOrDefault(String sortOrder, String defaultSortOrder) {
        Sort.Direction direction = parseDirection(sortOrder);
        if (direction != null) {
            return direction;
        }
        direction = parseDirection(defaultSortOrder);
        if (direction != null) {
            return direction;
        }
        return DEFAULT_SORT_DIRECTION;
    }

    private static boolean isSupportedSortField(String sortField) {
        return StringUtils.isNotBlank(sortField) && SUPPORTED_SORT_FIELDS.contains(sortField.trim());
    }

    private static Sort.Direction parseDirection(String sortOrder) {
        if (StringUtils.isBlank(sortOrder)) {
            return null;
        }
        try {
            return Sort.Direction.fromString(sortOrder.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
