package com.triphuddle.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code integration.openai}; blank model, base URL and token limit fall back to defaults.
 *
 * @param enabled whether the OpenAI integration is switched on
 * @param apiKey the OpenAI API key
 * @param model the model to use
 * @param baseUrl the base URL of the OpenAI API
 * @param temperature the sampling temperature
 * @param maxTokens the maximum number of tokens to generate
 */
@ConfigurationProperties(prefix = "integration.openai")
public record OpenAiProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        int maxTokens
) {

    public OpenAiProperties {
        model = model == null || model.isBlank() ? "gpt-4o-mini" : model;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        maxTokens = maxTokens <= 0 ? 800 : maxTokens;
    }
}
