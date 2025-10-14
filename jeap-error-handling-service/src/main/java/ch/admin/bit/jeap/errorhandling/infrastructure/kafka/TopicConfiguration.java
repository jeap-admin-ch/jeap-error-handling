package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;


import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Configuration
@Slf4j
class TopicConfiguration {
    static final String NAME = "${jeap.errorhandling.topic}";

    static final String DEAD_LETTER_TOPIC_NAME = "${jeap.errorhandling.deadLetterTopicName}";

    static final String ERROR_TOPIC_NAME = "${jeap.messaging.kafka.errorTopicName}";

    @Getter
    @Value(NAME)
    private String topicName;

    @Value(DEAD_LETTER_TOPIC_NAME)
    private String deadLetterTopicName;

    @Value(ERROR_TOPIC_NAME)
    private String errorTopicName;

    @Value("#{${jeap.messaging.kafka.cluster:{}}}")
    private Map<String, Map<String, String>> multiClusterConfiguration = Map.of();

    @Configuration
    @Profile("cloud")
    @RequiredArgsConstructor
    @SuppressWarnings({"squid:S3985", "unused"})
    private static class TopicConfigurationCloud {
        private final KafkaAdmin kafkaAdmin;
        private final TopicConfiguration topicConfiguration;

        @PostConstruct
        @SuppressWarnings("findbugs:RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
        public void checkIfTopicExist() throws ExecutionException, InterruptedException {
            try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
                adminClient.describeTopics(List.of(topicConfiguration.getTopicName())).allTopicNames().get();
            }
        }
    }

    @PostConstruct
    public void checkTopicsConfiguration() {
        log.info("Configured values for both error topics: deadLetterTopicName={}, errorTopicName={}", deadLetterTopicName, errorTopicName);
        if (!StringUtils.hasText(deadLetterTopicName)) {
            throw new IllegalArgumentException("Dead letter topic name is required to start this application. Please configure the property " + DEAD_LETTER_TOPIC_NAME);
        }
        if (!deadLetterTopicName.equals(errorTopicName)) {
            throw new IllegalArgumentException("A configuration was found for " + ERROR_TOPIC_NAME + " (" + errorTopicName + "). This parameter must not be configured for the error handling service.");
        }
        multiClusterConfiguration.keySet().forEach(clusterName -> {
            String multiClusterErrorTopicName = multiClusterConfiguration.get(clusterName).get("errorTopicName");
            if (StringUtils.hasText(multiClusterErrorTopicName) && !deadLetterTopicName.equals(multiClusterErrorTopicName)) {
                throw new IllegalArgumentException("A configuration was found for errorTopicName for cluster '" + clusterName + "': '" + multiClusterErrorTopicName + "'. This parameter cannot be different to the value of " + DEAD_LETTER_TOPIC_NAME + ", which is '" + deadLetterTopicName + "'.");
            }
        });
    }
}
