package ch.admin.bit.jeap.errorhandling.web.api;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

class DateTimeUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static String timestamp(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

}
