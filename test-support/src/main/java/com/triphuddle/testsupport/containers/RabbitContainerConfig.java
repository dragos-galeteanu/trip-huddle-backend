package com.triphuddle.testsupport.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * RabbitMQ in a container, for messaging tests that send real messages to a service's listeners.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitContainerConfig {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:3.13-alpine");
    }
}
