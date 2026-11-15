package com.triphuddle.openai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triphuddle.amqp.ai.AiCitation;
import com.triphuddle.openai.config.OpenAiProperties;
import com.triphuddle.openai.config.OpenAiWorkerProperties;
import com.triphuddle.openai.exception.OpenAiCallException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI's Responses and embeddings endpoints; one call each, retries are the caller's.
 */
public class OpenAiApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;
    private final OpenAiWorkerProperties workerProperties;

    /**
     * @param restClient the HTTP client, already pointed at the OpenAI base URL
     * @param objectMapper the JSON mapper
     * @param properties the OpenAI settings
     * @param workerProperties the worker settings
     */
    public OpenAiApiClient(RestClient restClient, ObjectMapper objectMapper, OpenAiProperties properties,
                           OpenAiWorkerProperties workerProperties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.workerProperties = workerProperties;
    }

    /**
     * Generate text, or JSON matching a schema, through {@code POST /responses}.
     *
     * @param generation the call
     * @return the answer
     * @throws OpenAiCallException if the call failed
     */
    public TextResult generate(TextGeneration generation) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", generation.model());
        if (generation.instructions() != null && !generation.instructions().isBlank()) {
            body.put("instructions", generation.instructions());
        }
        body.put("input", generation.input());
        body.put("max_output_tokens", generation.maxOutputTokens());
        // Answers are kept by the requester; OpenAI need not store them.
        body.put("store", false);
        if (generation.isWebSearch()) {
            body.putArray("tools").addObject().put("type", workerProperties.webSearchTool());
        }
        if (generation.schema() != null) {
            ObjectNode format = body.putObject("text").putObject("format");
            format.put("type", "json_schema");
            format.put("name", generation.schemaName());
            format.put("strict", true);
            format.set("schema", generation.schema());
        }
        return parseText(post("/responses", body));
    }

    /**
     * Embed texts through {@code POST /embeddings}.
     *
     * @param model the embedding model
     * @param inputs the texts
     * @return one embedding per input, in order
     * @throws OpenAiCallException if the call failed
     */
    public EmbeddingResult embed(String model, List<String> inputs) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode input = body.putArray("input");
        inputs.forEach(input::add);
        body.put("encoding_format", "float");
        JsonNode root = post("/embeddings", body);

        float[][] vectors = new float[inputs.size()][];
        for (JsonNode item : root.path("data")) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= vectors.length) {
                throw new OpenAiCallException("OpenAI returned an embedding for unknown input " + index, false);
            }
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            vectors[index] = vector;
        }
        for (int i = 0; i < vectors.length; i++) {
            if (vectors[i] == null) {
                throw new OpenAiCallException("OpenAI returned no embedding for input " + i, true);
            }
        }
        return new EmbeddingResult(List.of(vectors), root.path("model").asText(model),
                root.path("usage").path("prompt_tokens").asLong());
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .body(body)
                    .exchange((request, response) -> {
                        String raw = new String(response.getBody().readAllBytes());
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return objectMapper.readTree(raw);
                        }
                        throw failure(status.value(), raw, response.getHeaders().getFirst("Retry-After"));
                    });
        } catch (OpenAiCallException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new OpenAiCallException("OpenAI did not answer in time: " + e.getMessage(), true, null, null, e);
        } catch (RuntimeException e) {
            throw new OpenAiCallException("Calling OpenAI failed: " + e.getMessage(), true, null, null, e);
        }
    }

    // Rate limits and server errors are worth another try; a bad request or a wrong key is not.
    private OpenAiCallException failure(int status, String raw, String retryAfter) {
        boolean isRetryable = status == 408 || status == 409 || status == 429 || status >= 500;
        String message = "OpenAI answered %d: %s".formatted(status, errorMessageOf(raw));
        return new OpenAiCallException(message, isRetryable, status, parseRetryAfter(retryAfter), null);
    }

    private String errorMessageOf(String raw) {
        try {
            JsonNode message = objectMapper.readTree(raw).path("error").path("message");
            return message.isMissingNode() ? raw : message.asText();
        } catch (IOException e) {
            return raw;
        }
    }

    private static Duration parseRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofMillis((long) (Double.parseDouble(value.trim()) * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // The answer is in the "message" items of output; web search calls and reasoning are other items.
    private static TextResult parseText(JsonNode root) {
        StringBuilder text = new StringBuilder();
        String refusal = null;
        Map<String, AiCitation> citations = new LinkedHashMap<>();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                String type = content.path("type").asText();
                if ("output_text".equals(type)) {
                    text.append(content.path("text").asText());
                    for (JsonNode annotation : content.path("annotations")) {
                        if ("url_citation".equals(annotation.path("type").asText())) {
                            String url = annotation.path("url").asText();
                            String title = annotation.path("title").isMissingNode() || annotation.path("title").isNull()
                                    ? null : annotation.path("title").asText();
                            citations.putIfAbsent(url, new AiCitation(url, title));
                        }
                    }
                } else if ("refusal".equals(type)) {
                    refusal = content.path("refusal").asText();
                }
            }
        }
        boolean isIncomplete = "incomplete".equals(root.path("status").asText());
        JsonNode usage = root.path("usage");
        return new TextResult(text.isEmpty() ? null : text.toString(), new ArrayList<>(citations.values()),
                root.path("model").asText(null), usage.path("input_tokens").asLong(),
                usage.path("output_tokens").asLong(), isIncomplete, refusal);
    }
}
