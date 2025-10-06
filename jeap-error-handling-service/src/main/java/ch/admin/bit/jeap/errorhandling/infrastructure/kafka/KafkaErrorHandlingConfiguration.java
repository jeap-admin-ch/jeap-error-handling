package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.messaging.kafka.errorhandling.ErrorServiceSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import static org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS;

@Configuration
@EnableConfigurationProperties(KafkaErrorHandlingConfigProperties.class)
public class KafkaErrorHandlingConfiguration {

    public static final String BACKOFF_BEAN_NAME = "ehsKafkaErrorHandlingBackOff";

    // Configure a retry mechanism for Kafka listeners that only retries messages with specific exceptions
    // and sends messages with other exceptions to the dead letter topic using the error sender service.
    @Bean
    CommonErrorHandler errorHandler(ErrorServiceSender errorServiceSender, @Qualifier(BACKOFF_BEAN_NAME) BackOff backOff) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(errorServiceSender, backOff);
        // don't retry by default
        errorHandler.defaultFalse();
        // retry only on a RecoverableEhsProcessingException
        errorHandler.addRetryableExceptions(RecoverableEhsProcessingException.class);
        return errorHandler;
    }

    @Bean(name = BACKOFF_BEAN_NAME)
    @ConditionalOnMissingBean(name = BACKOFF_BEAN_NAME)
    BackOff ehsKafkaErrorHandlingBackOff(KafkaErrorHandlingConfigProperties kafkaErrorHandlingConfigProperties) {
        return new FixedBackOff(kafkaErrorHandlingConfigProperties.getRetryInterval().toMillis(), UNLIMITED_ATTEMPTS);
    }

}
