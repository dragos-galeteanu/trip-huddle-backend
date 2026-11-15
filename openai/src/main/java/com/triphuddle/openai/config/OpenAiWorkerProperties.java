package com.triphuddle.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Settings under {@code integration.openai.worker}, for the AI jobs other services send over amqp.
 * Zero or negative numbers and blank names fall back to defaults.
 *
 * @param taskModels the model for each task, e.g. {@code itinerary: gpt-4.1}; other tasks use
 *                   {@code integration.openai.model}
 * @param embeddingModel the embedding model
 * @param maxOutputTokens the default limit on tokens a text answer may use
 * @param maxAttempts how many calls a job may take: for rate limits, server errors and answers that are cut
 *                    short or not valid JSON
 * @param retryBaseMs the pause before the first retry, doubled for each one after
 * @param maxRetryMs the longest pause, also the cap on what OpenAI's Retry-After may ask for
 * @param readTimeoutSeconds how long to wait for an answer; web search answers are slow
 * @param webSearchTool the Responses API tool type for web search
 */
@ConfigurationProperties(prefix = "integration.openai.worker")
public record OpenAiWorkerProperties(
        Map<String, String> taskModels,
        String embeddingModel,
        int maxOutputTokens,
        int maxAttempts,
        long retryBaseMs,
        long maxRetryMs,
        int readTimeoutSeconds,
        String webSearchTool
) {

    public OpenAiWorkerProperties {
        taskModels = taskModels == null ? Map.of() : Map.copyOf(taskModels);
        embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "text-embedding-3-small" : embeddingModel;
        maxOutputTokens = maxOutputTokens <= 0 ? 4000 : maxOutputTokens;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryBaseMs = retryBaseMs <= 0 ? 2000 : retryBaseMs;
        maxRetryMs = maxRetryMs <= 0 ? 30000 : maxRetryMs;
        readTimeoutSeconds = readTimeoutSeconds <= 0 ? 180 : readTimeoutSeconds;
        webSearchTool = webSearchTool == null || webSearchTool.isBlank() ? "web_search" : webSearchTool;
    }
}
