package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import ch.admin.bit.jeap.errorhandling.infrastructure.kafka.converters.ProcessingFailedEventConverter;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaBeanNames;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaPropertyFactory;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class KafkaMessageProcessingFailedEventConsumerFactory implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware, EnvironmentAware {

    @Setter
    private Environment environment;
    @Setter
    private BeanFactory beanFactory;
    private String defaultClusterName;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        defaultClusterName = kafkaProperties.getDefaultClusterName();
        kafkaProperties
                .clusterNames()
                .forEach(clusterName -> registerConsumerContainerBeanDefinition(registry, clusterName));
    }

    private void registerConsumerContainerBeanDefinition(BeanDefinitionRegistry registry, String clusterName) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ConcurrentMessageListenerContainer.class);
        beanDefinition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, clusterName));
        beanDefinition.setInstanceSupplier(() -> createContainer(clusterName));
        registry.registerBeanDefinition("message-processing-failed-container-" + clusterName, beanDefinition);
    }

    private ConcurrentKafkaListenerContainerFactory<?, ?> getContainerFactory(String clusterName) {
        String listenerContainerFactoryBeanName = new JeapKafkaBeanNames(defaultClusterName).getListenerContainerFactoryBeanName(clusterName);
        return (ConcurrentKafkaListenerContainerFactory<?, ?>) beanFactory.getBean(listenerContainerFactoryBeanName);
    }

    private ConcurrentMessageListenerContainer<?, ?> createContainer(String clusterName) {
        TopicConfiguration topicConfiguration = beanFactory.getBean(TopicConfiguration.class);
        ConcurrentMessageListenerContainer<?, ?> container = getContainerFactory(clusterName).createContainer(topicConfiguration.getTopicName());
        ErrorEventHandler errorEventHandler = beanFactory.getBean(ErrorEventHandler.class);
        ProcessingFailedEventConverter processingFailedEventConverter = beanFactory.getBean(ProcessingFailedEventConverter.class);
        MessageProcessingFailedEventListener listener = new MessageProcessingFailedEventListener(
                processingFailedEventConverter, errorEventHandler, clusterName);
        container.setupMessageListener(listener);
        return container;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nop
    }
}
