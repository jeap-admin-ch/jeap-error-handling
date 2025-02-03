package ch.admin.bit.jeap.errorhandling.infrastructure.persistence;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PersistenceTestConfig {

    @Bean
    @Primary
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Force baselining, see comment in src/test/resources/application.yml
            flyway.baseline();
            flyway.migrate();
        };
    }
}
