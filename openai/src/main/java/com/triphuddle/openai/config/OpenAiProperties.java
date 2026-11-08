package com.triphuddle.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
