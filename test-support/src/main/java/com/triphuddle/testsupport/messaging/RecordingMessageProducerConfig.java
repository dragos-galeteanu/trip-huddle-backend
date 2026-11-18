package com.triphuddle.testsupport.messaging;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Puts a {@link RecordingMessageProducer} in front of the real producer. With the broker off (rabbitmq.enabled is
 * false), also stands in for the listener container factory the listeners name, with containers that never start:
 * the listeners exist and tests call them directly.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingMessageProducerConfig {

    @Bean
    @Primary
    RecordingMessageProducer recordingMessageProducer() {
        return new RecordingMessageProducer();
    }

    @Bean
    @ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "false")
    SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAutoStartup(false);
        return factory;
    }
}
