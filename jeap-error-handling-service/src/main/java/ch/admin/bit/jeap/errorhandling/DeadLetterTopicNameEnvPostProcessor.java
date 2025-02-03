package ch.admin.bit.jeap.errorhandling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

public class DeadLetterTopicNameEnvPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> map = Map.of(
                "jeap.messaging.kafka.errorTopicName", "${jeap.errorhandling.deadLetterTopicName}"
        );
        addHighPriorityPropertySource(environment, new MapPropertySource(getClass().getSimpleName(), map));
    }

    private static void addHighPriorityPropertySource(ConfigurableEnvironment configurableEnvironment, PropertySource<?> source) {
        // Add property source before properties attributes on tests to be able to override specific properties for tests as required
        // https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config
        if (!configurableEnvironment.getPropertySources().contains(source.getName())) {
            configurableEnvironment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, source);
        }
    }
}