package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.error-list")
public class ErrorListConfigProperties {

    static final String DEFAULT_STATE_FILTER = "PERMANENT";
    private static final Set<String> VALID_STATE_FILTERS = Set.of("PERMANENT", "TEMPORARY", "RETRIED", "DELETED");

    /**
     * Default value of the "no Jira ticket" filter in the error list view. A value persisted locally by the
     * user in the browser takes precedence over this default.
     */
    private boolean defaultNoTicketFilter = false;

    /**
     * Default value of the error state filter in the error list view (PERMANENT, TEMPORARY, RETRIED or DELETED).
     * A value persisted locally by the user in the browser takes precedence over this default.
     */
    private String defaultStateFilter = DEFAULT_STATE_FILTER;

    public String getDefaultStateFilter() {
        if (!VALID_STATE_FILTERS.contains(defaultStateFilter)) {
            log.warn("Invalid default state filter '{}' configured, falling back to '{}'. Valid values: {}",
                    defaultStateFilter, DEFAULT_STATE_FILTER, VALID_STATE_FILTERS);
            return DEFAULT_STATE_FILTER;
        }
        return defaultStateFilter;
    }
}
