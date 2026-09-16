package ch.admin.bit.jeap.errorhandling.domain.eventhandler;

final class ErrorEventTextSanitizer {

    private ErrorEventTextSanitizer() {
    }

    /**
     * PostgreSQL text columns cannot store NUL characters.
     */
    static String sanitize(String value) {
        return value == null ? null : value.replace('\u0000', ' ');
    }
}
