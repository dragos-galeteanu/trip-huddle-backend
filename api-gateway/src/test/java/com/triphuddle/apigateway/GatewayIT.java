package com.triphuddle.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Routing, the public routes and CORS, with a plain HTTP server standing in for every service: discovery is the
 * simple client pointing each service ID at it, and requests carry test JWTs.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=true",
        "management.tracing.enabled=false"})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GatewayIT {

    static final List<String> SERVICES = List.of("API-USER", "API-GROUP", "API-CHAT", "API-LICENSE", "API-PAYMENT",
            "API-PLATFORM");
    // api-destination is down in these tests: a port nothing listens on.
    static final int DEAD_PORT;
    static final HttpServer BACKEND;
    // Each request the backend got, as "METHOD path".
    static final Queue<String> RECEIVED = new ConcurrentLinkedQueue<>();

    static {
        try {
            BACKEND = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        BACKEND.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String seen = exchange.getRequestMethod() + " " + exchange.getRequestURI();
            RECEIVED.add(seen);
            byte[] body = ("{\"seen\": \"" + seen + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        BACKEND.start();
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            DEAD_PORT = socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        for (String service : SERVICES) {
            String uri = "http://localhost:" + BACKEND.getAddress().getPort();
            registry.add("spring.cloud.discovery.client.simple.instances[" + service + "][0].uri", () -> uri);
            registry.add("spring.cloud.discovery.client.simple.instances[" + service.toLowerCase()
                    + "][0].uri", () -> uri);
        }
        registry.add("spring.cloud.discovery.client.simple.instances[API-DESTINATION][0].uri",
                () -> "http://localhost:" + DEAD_PORT);
        registry.add("spring.cloud.discovery.client.simple.instances[api-destination][0].uri",
                () -> "http://localhost:" + DEAD_PORT);
    }

    @AfterAll
    static void stop() {
        BACKEND.stop(0);
    }

    @Autowired ApplicationContext context;
    WebTestClient client;

    @BeforeEach
    void client() {
        RECEIVED.clear();
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    WebTestClient.ResponseSpec anonymous(HttpMethod method, String path) {
        return client.method(method).uri(path).exchange();
    }

    WebTestClient.ResponseSpec signedIn(HttpMethod method, String path) {
        return client.mutateWith(mockJwt().jwt(jwt -> jwt.claim("role", "Customer"))).method(method).uri(path)
                .exchange();
    }

    @Nested
    class Routing {

        @Test
        void each_services_paths_reach_it_unchanged() {
            for (String path : List.of("/api/v1/users/me", "/api/v1/groups/1",
                    "/api/v1/chats/conversations", "/api/v1/licenses/mine", "/api/v1/payments/invoices",
                    "/api/v1/platform/feature")) {
                signedIn(HttpMethod.GET, path).expectStatus().isOk();
            }

            assertThat(RECEIVED).containsExactly("GET /api/v1/users/me", "GET /api/v1/groups/1", "GET /api/v1/chats/conversations", "GET /api/v1/licenses/mine",
                    "GET /api/v1/payments/invoices", "GET /api/v1/platform/feature");
        }

        @Test
        void a_services_api_docs_are_rewritten_to_its_own_path_and_need_a_token() {
            signedIn(HttpMethod.GET, "/api-user/v3/api-docs").expectStatus().isOk();
            anonymous(HttpMethod.GET, "/api-chat/v3/api-docs").expectStatus().isUnauthorized();

            assertThat(RECEIVED).containsExactly("GET /api-docs");
        }

        @Test
        void a_customer_cannot_read_or_change_the_gateways_routes() {
            signedIn(HttpMethod.GET, "/actuator/gateway/routes").expectStatus().isForbidden();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.claim("role", "Customer"))).post()
                    .uri("/actuator/gateway/routes/evil")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("{\"uri\": \"https://evil.example\", \"predicates\": [\"Path=/api/v1/users/**\"]}")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        void the_health_check_is_open() {
            anonymous(HttpMethod.GET, "/actuator/health").expectStatus().isOk();
        }
    }

    @Nested
    class Public_routes {

        @Test
        void the_routes_services_mark_public_pass_without_a_token() {
            anonymous(HttpMethod.GET, "/api/v1/platform/countries").expectStatus().isOk();
            anonymous(HttpMethod.GET, "/api/v1/platform/currencies/1").expectStatus().isOk();
            anonymous(HttpMethod.GET, "/api/v1/platform/languages").expectStatus().isOk();
            anonymous(HttpMethod.GET, "/api/v1/licenses").expectStatus().isOk();
            anonymous(HttpMethod.GET, "/api/v1/licenses/1/price").expectStatus().isOk();
            anonymous(HttpMethod.POST, "/api/v1/payments/webhook").expectStatus().isOk();
            anonymous(HttpMethod.GET, "/api/v1/users/verify-email?token=x").expectStatus().isOk();
            anonymous(HttpMethod.POST, "/api/v1/groups/invitations/accept").expectStatus().isOk();
            anonymous(HttpMethod.POST, "/api/v1/groups/invitations/reject").expectStatus().isOk();

            assertThat(RECEIVED).hasSize(9);
        }

        @Test
        void everything_else_needs_a_token() {
            anonymous(HttpMethod.POST, "/api/v1/platform/countries").expectStatus().isUnauthorized();
            anonymous(HttpMethod.DELETE, "/api/v1/licenses/1").expectStatus().isUnauthorized();
            anonymous(HttpMethod.GET, "/api/v1/licenses/mine/subscriptions").expectStatus().isUnauthorized();
            anonymous(HttpMethod.GET, "/api/v1/groups/invitations/accept").expectStatus().isUnauthorized();
            anonymous(HttpMethod.GET, "/api/v1/destinations/places").expectStatus().isUnauthorized();
            anonymous(HttpMethod.GET, "/elsewhere").expectStatus().isUnauthorized();

            assertThat(RECEIVED).isEmpty();
        }
    }

    @Nested
    class Cors {

        @Test
        void the_web_app_may_call_the_api_and_other_sites_may_not() {
            client.options().uri("http://localhost/api/v1/groups")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

            client.options().uri("http://localhost/api/v1/groups")
                    .header(HttpHeaders.ORIGIN, "https://evil.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    class Fallback {

        @Test
        void a_service_that_is_down_answers_503() {
            signedIn(HttpMethod.GET, "/api/v1/destinations/places").expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unavailable"));
        }
    }
}
