package com.triphuddle.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * With {@code app.swagger.public=true}, as in local development, the services' API docs open without a token while
 * the API itself still needs one.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=true",
        "management.tracing.enabled=false", "app.swagger.public=true"})
class PublicSwaggerIT {

    static final HttpServer BACKEND;

    static {
        try {
            BACKEND = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        BACKEND.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        BACKEND.start();
    }

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + BACKEND.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances[API-USER][0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances[api-user][0].uri", () -> uri);
    }

    @AfterAll
    static void stop() {
        BACKEND.stop(0);
    }

    @Autowired ApplicationContext context;

    @Test
    void theApiDocsOpenWithoutATokenButTheApiDoesNot() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity())
                .configureClient().build();

        client.get().uri("/api-user/v3/api-docs").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/users/me").exchange().expectStatus().isUnauthorized();
    }
}
