package ch.admin.bit.jeap.errorhandling.domain.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorGroupConfigPropertiesTest {

    @Test
    void shouldReturnConfiguredDefaultSortWhenValid() {
        ErrorGroupConfigProperties properties = new ErrorGroupConfigProperties();
        properties.setDefaultSortField("errorCount");
        properties.setDefaultSortOrder("ASC");

        assertThat(properties.getDefaultSortField()).isEqualTo("errorCount");
        assertThat(properties.getDefaultSortOrder()).isEqualTo("ASC");
    }

    @Test
    void shouldReturnFallbackDefaultSortWhenConfiguredValuesAreInvalid() {
        ErrorGroupConfigProperties properties = new ErrorGroupConfigProperties();
        properties.setDefaultSortField("unsupportedField");
        properties.setDefaultSortOrder("unsupportedOrder");

        assertThat(properties.getDefaultSortField()).isEqualTo("latestErrorAt");
        assertThat(properties.getDefaultSortOrder()).isEqualTo("DESC");
    }
}
