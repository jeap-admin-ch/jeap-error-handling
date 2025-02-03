package ch.admin.bit.jeap.errorhandling.domain.housekeeping;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Configuration for the automatic housekeeping service (Db clean up)
 */
@Configuration
@ConfigurationProperties(prefix = "jeap.errorhandling.housekeeping")
@Data
public class HouseKeepingServiceConfigProperties {

    /**
     * Delete errors older than this value [duration]. Default is 180 days
     */
    private Duration errorMaxAge = Duration.of(180, ChronoUnit.DAYS);

    /**
     * Size for the queries [pages]. Default is 100
     */
    private int pageSize = 100;
    /**
     * Max. pages to housekeep in one run. This limits the amount of time one housekeeping run can max. spend
     * (the time to delete maxPages * pageSize elements of each kind).
     */
    private int maxPages = 100000;
}
