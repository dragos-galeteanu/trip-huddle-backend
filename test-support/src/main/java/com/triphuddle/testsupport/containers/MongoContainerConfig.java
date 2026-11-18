package com.triphuddle.testsupport.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MongoDBContainer;

/**
 * MongoDB in a container, wired in as the Mongo connection.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoContainerConfig {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:7.0");
    }
}
