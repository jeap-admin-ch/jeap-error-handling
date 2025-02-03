package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.DefaultTaskFactory;
import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.DefaultTaskFactoryProperties;
import ch.admin.bit.jeap.errorhandling.domain.manualtask.taskFactory.TaskFactory;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementClient;
import ch.admin.bit.jeap.errorhandling.infrastructure.manualtask.TaskManagementServiceProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@AutoConfiguration
@EnableWebSecurity
@EnableTransactionManagement
@EnableJpaRepositories
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
@EnableConfigurationProperties
@ComponentScan(basePackages = {"ch.admin.bit.jeap.errorhandling"})
@EntityScan(basePackages = "ch.admin.bit.jeap.errorhandling")
@PropertySource("classpath:errorhandlerDefaultProperties.properties")
class ErrorHandlingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }

    /**
     * Declared as a bean with ConditionalOnMissingBean to allow overriding by Error Handling service instances
     */
    @Bean
    @ConditionalOnMissingBean(TaskFactory.class)
    TaskFactory defaultTaskFactor(DefaultTaskFactoryProperties defaultTaskFactoryProperties,
                                  TaskManagementServiceProperties taskManagementServiceProperties,
                                  TaskManagementClient taskManagementClient) {
        return new DefaultTaskFactory(defaultTaskFactoryProperties,
                taskManagementServiceProperties,
                taskManagementClient);
    }
}