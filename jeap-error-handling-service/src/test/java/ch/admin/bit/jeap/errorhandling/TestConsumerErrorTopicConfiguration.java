package ch.admin.bit.jeap.errorhandling;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.KafkaErrorHandlingConfiguration;
import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.RecoverableEhsProcessingException;
import ch.admin.bit.jeap.messaging.kafka.errorhandling.ErrorServiceFailedHandler;
import ch.admin.bit.jeap.messaging.kafka.errorhandling.ErrorServiceSender;
import ch.admin.bit.jeap.messaging.kafka.errorhandling.StackTraceHasher;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import ch.admin.bit.jeap.messaging.kafka.tracing.TracerBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Test-only configuration that lets the failing test consumers publish their failures to the topic the error
 * handling service consumes from, instead of to its dead letter topic.
 * <p>
 * In production the two topics belong to different applications: the message consumers of a system publish
 * their failures to the error topic ({@code jeap.messaging.kafka.errorTopicName}), the error handling service
 * consumes that topic ({@code jeap.errorhandling.topic}) and publishes its own failures to the dead letter
 * topic ({@code jeap.errorhandling.deadLetterTopicName}). In an integration test the failing test consumer
 * shares the application context - and therefore the single error handler routing failures to the error topic,
 * which {@code DeadLetterTopicNameEnvPostProcessor} pins to the dead letter topic - with the error handling
 * service.
 * <p>
 * This configuration separates the two again by replacing that error handler with one that recovers a failed
 * message with the error topic of the application it belongs to: messages the error handling service itself
 * failed on go to its dead letter topic as they do in production, the failures of all other consumers - the
 * test consumers - go to the topic the error handling service consumes from, as a separate application would
 * publish them. The failed events themselves are still produced by {@link ErrorServiceSender}, so they are
 * built exactly as in production.
 * <p>
 * Activated with the profile {@value #PROFILE}.
 */
@Slf4j
@Configuration
@Profile(TestConsumerErrorTopicConfiguration.PROFILE)
class TestConsumerErrorTopicConfiguration {

    static final String PROFILE = "test-consumer-error-topic";

    /**
     * Replaces the error handler of {@link KafkaErrorHandlingConfiguration}, which routes every failure to the
     * dead letter topic. Configured with the same retry behaviour, only the recoverer differs.
     */
    @Bean
    @Primary
    CommonErrorHandler testConsumerErrorTopicErrorHandler(ErrorServiceSender deadLetterTopicSender,
                                                          @Qualifier(KafkaErrorHandlingConfiguration.BACKOFF_BEAN_NAME) BackOff backOff,
                                                          @Value("${jeap.errorhandling.topic}") String errorTopicName,
                                                          BeanFactory beanFactory,
                                                          ErrorServiceFailedHandler errorServiceFailedHandler,
                                                          ObjectProvider<TracerBridge> tracerBridge,
                                                          Environment environment) {
        log.info("Publishing the failures of the test consumers to topic {}", errorTopicName);
        ErrorServiceSender errorTopicSender = createErrorTopicSender(errorTopicName, beanFactory,
                errorServiceFailedHandler, tracerBridge, environment);
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            boolean failedInErrorHandlingService = errorTopicName.equals(record.topic());
            ErrorServiceSender sender = failedInErrorHandlingService ? deadLetterTopicSender : errorTopicSender;
            sender.accept(record, exception);
        };
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // don't retry by default
        errorHandler.defaultFalse();
        // retry only on a RecoverableEhsProcessingException
        errorHandler.addRetryableExceptions(RecoverableEhsProcessingException.class);
        return errorHandler;
    }

    /**
     * An {@link ErrorServiceSender} publishing to the given topic instead of to the error topic configured for
     * the application. The tests configure a single Kafka cluster, so overriding the error topic name of the
     * properties is enough - {@link KafkaProperties#getErrorTopicName(String)} falls back to it for every
     * cluster name.
     */
    private ErrorServiceSender createErrorTopicSender(String errorTopicName, BeanFactory beanFactory,
                                                      ErrorServiceFailedHandler errorServiceFailedHandler,
                                                      ObjectProvider<TracerBridge> tracerBridge,
                                                      Environment environment) {
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        kafkaProperties.setErrorTopicName(errorTopicName);
        BackOff retrySendingError = new FixedBackOff(kafkaProperties.getErrorServiceRetryIntervalMs(),
                kafkaProperties.getErrorServiceRetryAttempts());
        return new ErrorServiceSender(beanFactory, kafkaProperties, errorServiceFailedHandler, retrySendingError,
                tracerBridge.getIfAvailable(() -> TracerBridge.NOOP), new StackTraceHasher(kafkaProperties));
    }
}
