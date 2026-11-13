package com.triphuddle.openai.config;

import com.triphuddle.openai.api.AiTextGenerator;
import com.triphuddle.openai.client.DisabledAiTextGenerator;
import com.triphuddle.openai.client.OpenAiChatCompletionsTextGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Wires the {@link AiTextGenerator}: the OpenAI client when enabled, a refusing stand-in otherwise.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiAutoConfiguration {

    /**
     * The OpenAI-backed generator, created only when {@code integration.openai.enabled} is true.
     *
     * @param restClientBuilder the builder for the HTTP client
     * @param objectMapper the JSON mapper used to read responses
     * @param properties the OpenAI settings
     * @return the OpenAI text generator
     */
    @Bean
    @ConditionalOnProperty(prefix = "integration.openai", name = "enabled", havingValue = "true")
    public AiTextGenerator openAiTextGenerator(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, OpenAiProperties properties) {
        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
        return new OpenAiChatCompletionsTextGenerator(restClient, objectMapper, properties);
    }

    /**
     * The fallback generator, used when no other {@link AiTextGenerator} is defined.
     *
     * @return a generator that refuses every request
     */
    @Bean
    @ConditionalOnMissingBean(AiTextGenerator.class)
    public AiTextGenerator disabledAiTextGenerator() {
        return new DisabledAiTextGenerator();
    }
}
