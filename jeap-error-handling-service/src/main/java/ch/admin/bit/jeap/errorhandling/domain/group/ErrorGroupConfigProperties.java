package ch.admin.bit.jeap.errorhandling.domain.group;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;


@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.error-groups")
public class ErrorGroupConfigProperties {

    /**
     * If errors should be collected into error groups or not.
     */
    private boolean errorGroupingEnabled = true;
    private String defaultSortField = ErrorGroupSort.DEFAULT_SORT_FIELD;
    private String defaultSortOrder = ErrorGroupSort.DEFAULT_SORT_DIRECTION.name();

    @Valid
    @NestedConfigurationProperty
    private ErrorGroupIssueTrackingProperties issueTracking;

    public boolean isIssueTrackingEnabled() {
        return issueTracking != null;
    }

    public String getDefaultSortField() {
        return ErrorGroupSort.validSortFieldOrDefault(defaultSortField, ErrorGroupSort.DEFAULT_SORT_FIELD);
    }

    public String getDefaultSortOrder() {
        return ErrorGroupSort.validSortDirectionOrDefault(defaultSortOrder, ErrorGroupSort.DEFAULT_SORT_DIRECTION.name()).name();
    }

}
