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

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "integration.openai", name = "enabled", havingValue = "true")
    public AiTextGenerator openAiTextGenerator(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, OpenAiProperties properties) {
        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
        return new OpenAiChatCompletionsTextGenerator(restClient, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AiTextGenerator.class)
    public AiTextGenerator disabledAiTextGenerator() {
        return new DisabledAiTextGenerator();
    }
}
