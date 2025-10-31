package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

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

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        String defaultClusterName = kafkaProperties.getDefaultClusterName();
        log.info("Registering MessageProcessingFailedEventListener container for default cluster '{}'", defaultClusterName);
        registerConsumerContainerBeanDefinition(registry, defaultClusterName);
    }

    private void registerConsumerContainerBeanDefinition(BeanDefinitionRegistry registry, String clusterName) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ConcurrentMessageListenerContainer.class);
        beanDefinition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, clusterName));
        beanDefinition.setInstanceSupplier(() -> createContainer(clusterName));
        registry.registerBeanDefinition("message-processing-failed-container-" + clusterName, beanDefinition);
    }

    private ConcurrentMessageListenerContainer<?, ?> createContainer(String clusterName) {
        TopicConfiguration topicConfiguration = beanFactory.getBean(TopicConfiguration.class);
        ConcurrentMessageListenerContainer<?, ?> container = getContainerFactory(clusterName).createContainer(topicConfiguration.getTopicName());
        ErrorEventHandler errorEventHandler = beanFactory.getBean(ErrorEventHandler.class);
        MessageProcessingFailedEventListener listener = new MessageProcessingFailedEventListener(errorEventHandler, clusterName);
        container.setupMessageListener(listener);
        return container;
    }

    private ConcurrentKafkaListenerContainerFactory<?, ?> getContainerFactory(String clusterName) {
        String listenerContainerFactoryBeanName = new JeapKafkaBeanNames(clusterName).getListenerContainerFactoryBeanName(clusterName);
        return (ConcurrentKafkaListenerContainerFactory<?, ?>) beanFactory.getBean(listenerContainerFactoryBeanName);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nop
    }
}
