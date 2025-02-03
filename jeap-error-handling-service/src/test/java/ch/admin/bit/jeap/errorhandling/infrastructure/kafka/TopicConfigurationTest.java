package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopicConfigurationTest {

    @Test
    void checkTopicsConfiguration_allOK(){
        TopicConfiguration topicConfiguration = new TopicConfiguration();

        ReflectionTestUtils.setField(topicConfiguration, "deadLetterTopicName", "test");
        ReflectionTestUtils.setField(topicConfiguration, "errorTopicName", "test");
        topicConfiguration.checkTopicsConfiguration();

    }

    @Test
    void checkTopicsConfiguration_wrong_deadLetterTopicName(){
        TopicConfiguration topicConfiguration = new TopicConfiguration();

        ReflectionTestUtils.setField(topicConfiguration, "deadLetterTopicName", "test");
        ReflectionTestUtils.setField(topicConfiguration, "errorTopicName", "dummy");

        Exception exception = assertThrows(IllegalArgumentException.class, topicConfiguration::checkTopicsConfiguration);

        assertThat(exception.getMessage()).isEqualTo("A configuration was found for ${jeap.messaging.kafka.errorTopicName} (dummy). This parameter must not be configured for the error handling service.");

    }

}