package com.triphuddle.smoke.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** JSON over HTTP, the way a client of the system talks to it. */
public final class Http {

    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Http() {
    }

    /** A response's status and body. */
    public record Reply(int status, JsonNode body) {

        public Reply expect(int expected) {
            if (status != expected) {
                throw new AssertionError("Expected %d but got %d: %s".formatted(expected, status, body));
            }
            return this;
        }
    }

    public static Reply send(String method, String url, String token, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body instanceof String text ? text
                                : JSON.writeValueAsString(body)));
            }
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            String text = response.body();
            JsonNode parsed;
            try {
                parsed = text == null || text.isBlank() ? JSON.nullNode() : JSON.readTree(text);
            } catch (IOException notJson) {
                parsed = JSON.getNodeFactory().textNode(text);
            }
            return new Reply(response.statusCode(), parsed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
