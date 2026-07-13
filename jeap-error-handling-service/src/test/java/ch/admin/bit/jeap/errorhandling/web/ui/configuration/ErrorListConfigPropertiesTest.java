package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorListConfigPropertiesTest {

    @Test
    void defaults() {
        ErrorListConfigProperties properties = new ErrorListConfigProperties();

        assertFalse(properties.isDefaultNoTicketFilter());
        assertEquals("PERMANENT", properties.getDefaultStateFilter());
    }

    @Test
    void getDefaultStateFilter_validValueIsReturned() {
        ErrorListConfigProperties properties = new ErrorListConfigProperties();
        properties.setDefaultStateFilter("DELETED");

        assertEquals("DELETED", properties.getDefaultStateFilter());
    }

    @Test
    void getDefaultStateFilter_invalidValueFallsBackToDefault() {
        ErrorListConfigProperties properties = new ErrorListConfigProperties();
        properties.setDefaultStateFilter("NOT_A_STATE");

        assertEquals("PERMANENT", properties.getDefaultStateFilter());
    }
}
