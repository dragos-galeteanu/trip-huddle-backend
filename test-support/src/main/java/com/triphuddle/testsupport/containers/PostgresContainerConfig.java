package com.triphuddle.testsupport.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres in a container, wired in as the datasource; Flyway builds the schema on it at startup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    // A service needing extensions (api-destination: PostGIS, pgvector) sets this to the project's own image.
    static final String IMAGE = System.getProperty("testsupport.postgres.image", "postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
