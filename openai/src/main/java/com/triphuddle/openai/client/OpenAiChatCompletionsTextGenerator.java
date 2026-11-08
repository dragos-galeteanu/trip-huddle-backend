package com.triphuddle.openai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.openai.api.AiTextGenerator;
import com.triphuddle.openai.config.OpenAiProperties;
import com.triphuddle.openai.dto.GenerateTextRequest;
import com.triphuddle.openai.exception.OpenAiIntegrationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiChatCompletionsTextGenerator implements AiTextGenerator {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public OpenAiChatCompletionsTextGenerator(RestClient restClient, ObjectMapper objectMapper, OpenAiProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String generateText(GenerateTextRequest request) {
        if (request == null || request.userPrompt() == null || request.userPrompt().isBlank()) {
            throw new OpenAiIntegrationException("A non-empty user prompt is required for AI text generation.");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new OpenAiIntegrationException("OpenAI API key is not configured. Set integration.openai.api-key.");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", safeSystemPrompt(request.systemPrompt())
        ));

        StringBuilder userMessage = new StringBuilder();
        if (request.context() != null && !request.context().isBlank()) {
            userMessage.append("Context:\n")
                    .append(request.context().trim())
                    .append("\n\n");
        }
        if (request.existingText() != null && !request.existingText().isBlank()) {
            userMessage.append("Current description:\n")
                    .append(request.existingText().trim())
                    .append("\n\n");
        }
        userMessage.append("Task:\n")
                .append(request.userPrompt().trim())
                .append("\n\nReturn only the final description text.");

        messages.add(Map.of(
                "role", "user",
                "content", userMessage.toString()
        ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.model());
        payload.put("temperature", properties.temperature());
        payload.put("max_tokens", properties.maxTokens());
        payload.put("messages", messages);

        try {
            String rawResponse = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new OpenAiIntegrationException("OpenAI returned an empty response.");
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isMissingNode() ? null : contentNode.asText();

            if (content == null || content.isBlank()) {
                throw new OpenAiIntegrationException("OpenAI did not return generated content.");
            }

            return content.trim();
        } catch (OpenAiIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OpenAiIntegrationException("Failed to generate text via OpenAI.", ex);
        }
    }

    private String safeSystemPrompt(String value) {
        if (value == null || value.isBlank()) {
            return "You are a professional writing assistant. Write clear, specific, concise descriptions suitable for resumes and professional documents.";
        }
        return value;
    }
}
