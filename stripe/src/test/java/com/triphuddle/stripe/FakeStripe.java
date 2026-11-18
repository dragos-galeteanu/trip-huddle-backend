package com.triphuddle.stripe;

import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for Stripe's API on localhost: answers each "METHOD /path" with the JSON queued for it, in order and
 * then repeating the last, and records every
 * request, so tests check what the clients actually send, form fields and headers included.
 */
public final class FakeStripe implements AutoCloseable {

    /**
     * One request as Stripe would receive it.
     */
    public record Request(String method, String path, Map<String, String> form, String idempotencyKey) {
    }

    private record Answer(int status, String json) {
    }

    private final HttpServer server;
    private final Map<String, Deque<Answer>> answers = new ConcurrentHashMap<>();
    private final Map<String, Answer> lastServed = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public FakeStripe() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public StripeClient client() {
        return StripeClient.builder()
                .setApiKey("sk_test_fake")
                .setApiBase("http://localhost:" + server.getAddress().getPort())
                .setMaxNetworkRetries(0)
                .build();
    }

    /** Answer the next "METHOD /path" with this JSON and status 200. */
    public FakeStripe on(String methodAndPath, String json) {
        return on(methodAndPath, 200, json);
    }

    public FakeStripe on(String methodAndPath, int status, String json) {
        answers.computeIfAbsent(methodAndPath, k -> new ArrayDeque<>()).add(new Answer(status, json));
        return this;
    }

    /** Answer with a Stripe error of this code. */
    public FakeStripe failing(String methodAndPath, int status, String code) {
        return on(methodAndPath, status, """
                {"error": {"type": "invalid_request_error", "code": "%s", "message": "%s"}}""".formatted(code, code));
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public Request only(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).reduce((a, b) -> {
            throw new AssertionError("More than one " + method + " " + path);
        }).orElseThrow(() -> new AssertionError("No " + method + " " + path + " in " + requests));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String query = exchange.getRequestURI().getRawQuery();
        String path = exchange.getRequestURI().getPath();
        requests.add(new Request(exchange.getRequestMethod(), path, form(body.isEmpty() ? query : body),
                exchange.getRequestHeaders().getFirst("Idempotency-Key")));
        // Answers are served in the order queued; once they run out, the last one served repeats.
        String key = exchange.getRequestMethod() + " " + path;
        Deque<Answer> queued = answers.get(key);
        Answer answer = queued != null && !queued.isEmpty() ? queued.poll() : lastServed.get(key);
        if (answer == null) {
            answer = new Answer(404, "{\"error\": {\"type\": \"invalid_request_error\", \"code\": \"no_fake\", "
                    + "\"message\": \"nothing queued for " + path + "\"}}");
        }
        lastServed.put(key, answer);
        byte[] json = answer.json().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Request-Id", "req_fake");
        exchange.sendResponseHeaders(answer.status(), json.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(json);
        }
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> form = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return form;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            form.put(URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8),
                    eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
