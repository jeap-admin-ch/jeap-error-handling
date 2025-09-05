package ch.admin.bit.jeap.errorhandling.web.api;

import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for handling date and time formatting and parsing operations.
 */
class DateTimeUtils {

    /**
     * Formatter for date and time in the pattern "yyyy-MM-dd HH:mm:ss".
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Formats the given {@link ZonedDateTime} into a string using the pattern "yyyy-MM-dd HH:mm:ss".
     *
     * @param dateTime the {@link ZonedDateTime} to format
     * @return the formatted date-time string, or {@code null} if {@code dateTime} is {@code null}
     */
    static String timestamp(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    /**
     * Parses the given date string into a {@link ZonedDateTime} object.
     * Returns null if the input is blank.
     *
     * @param date the date string to parse
     * @return the parsed {@link ZonedDateTime} or null if input is blank
     */
    static ZonedDateTime parseDate(String date) {
        return StringUtils.isBlank(date) ? null : ZonedDateTime.parse(date);
    }

}
