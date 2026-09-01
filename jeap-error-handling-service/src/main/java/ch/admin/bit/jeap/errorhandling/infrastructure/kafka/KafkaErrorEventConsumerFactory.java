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
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
class KafkaErrorEventConsumerFactory implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware, EnvironmentAware {

    @Setter
    private Environment environment;
    @Setter
    private BeanFactory beanFactory;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        KafkaProperties kafkaProperties = JeapKafkaPropertyFactory.createJeapKafkaProperties(environment);
        String defaultClusterName = kafkaProperties.getDefaultClusterName();
        log.info("Registering MessageProcessingFailedEvent listener container for default cluster '{}'", defaultClusterName);
        registerContainer(registry, "message-processing-failed-container-" + defaultClusterName,
                defaultClusterName, false);

        String modulithTopic = environment.getProperty(
                TopicConfiguration.MODULITH_PUBLICATION_PROCESSING_FAILED_TOPIC_PROPERTY, "");
        if (StringUtils.hasText(modulithTopic)) {
            log.info("Registering ModulithPublicationProcessingFailedEvent listener container for default cluster '{}'",
                    defaultClusterName);
            registerContainer(registry, "modulith-publication-processing-failed-container-" + defaultClusterName,
                    defaultClusterName, true);
        }
    }

    private void registerContainer(BeanDefinitionRegistry registry, String beanName, String clusterName,
                                   boolean modulithPublication) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(ConcurrentMessageListenerContainer.class);
        beanDefinition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, clusterName));
        beanDefinition.setInstanceSupplier(() -> createContainer(clusterName, modulithPublication));
        registry.registerBeanDefinition(beanName, beanDefinition);
    }

    private ConcurrentMessageListenerContainer<?, ?> createContainer(String clusterName, boolean modulithPublication) {
        TopicConfiguration topicConfiguration = beanFactory.getBean(TopicConfiguration.class);
        String topic = modulithPublication
                ? topicConfiguration.getModulithPublicationProcessingFailedTopic()
                : topicConfiguration.getTopicName();
        ConcurrentMessageListenerContainer<?, ?> container = getContainerFactory(clusterName).createContainer(topic);
        ErrorEventHandler errorEventHandler = beanFactory.getBean(ErrorEventHandler.class);
        container.setupMessageListener(modulithPublication
                ? new ModulithPublicationProcessingFailedEventListener(errorEventHandler, clusterName)
                : new MessageProcessingFailedEventListener(errorEventHandler, clusterName));
        return container;
    }

    private ConcurrentKafkaListenerContainerFactory<?, ?> getContainerFactory(String clusterName) {
        String beanName = new JeapKafkaBeanNames(clusterName).getListenerContainerFactoryBeanName(clusterName);
        return (ConcurrentKafkaListenerContainerFactory<?, ?>) beanFactory.getBean(beanName);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nop
    }
}
