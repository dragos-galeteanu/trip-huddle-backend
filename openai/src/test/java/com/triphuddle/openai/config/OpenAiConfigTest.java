package com.triphuddle.openai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.openai.api.AiTextGenerator;
import com.triphuddle.openai.client.DisabledAiTextGenerator;
import com.triphuddle.openai.client.OpenAiChatCompletionsTextGenerator;
import com.triphuddle.openai.worker.AiWorkerListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The OpenAI settings' defaults, and what is wired with OpenAI switched on or off.
 */
class OpenAiConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(OpenAiAutoConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ObjectMapper.class)
            .withBean(RabbitMQMessageProducer.class, () -> mock(RabbitMQMessageProducer.class));

    @Test
    void switchedOffTheTextGeneratorRefusesAndTheWorkerIsStillWired() {
        context.withPropertyValues("integration.openai.enabled=false").run(started -> {
            assertThat(started).hasSingleBean(AiWorkerListener.class);
            assertThat(started.getBean(AiTextGenerator.class)).isInstanceOf(DisabledAiTextGenerator.class);
        });
    }

    @Test
    void switchedOnTheTextGeneratorCallsOpenAi() {
        context.withPropertyValues("integration.openai.enabled=true", "integration.openai.api-key=sk-test")
                .run(started -> assertThat(started.getBean(AiTextGenerator.class))
                        .isInstanceOf(OpenAiChatCompletionsTextGenerator.class));
    }

    @Test
    void openAiSettingsLeftOutGetTheirDefaultsAndGivenOnesAreKept() {
        OpenAiProperties defaults = new OpenAiProperties(false, null, " ", null, 0.2, 0);
        assertThat(defaults.model()).isEqualTo("gpt-4o-mini");
        assertThat(defaults.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(defaults.maxTokens()).isEqualTo(800);
        assertThat(new OpenAiProperties(true, "k", null, " ", 0.2, 10).baseUrl())
                .isEqualTo("https://api.openai.com/v1");

        OpenAiProperties set = new OpenAiProperties(true, "k", "gpt-x", "https://openai.example/v1", 0.5, 100);
        assertThat(set.model()).isEqualTo("gpt-x");
        assertThat(set.baseUrl()).isEqualTo("https://openai.example/v1");
        assertThat(set.maxTokens()).isEqualTo(100);
    }

    @Test
    void workerSettingsLeftOutGetTheirDefaultsAndGivenOnesAreKept() {
        assertThat(new OpenAiWorkerProperties(null, " ", 0, 0, 0, 0, 0, null)).isEqualTo(new OpenAiWorkerProperties(
                Map.of(), "text-embedding-3-small", 4000, 3, 2000, 30000, 180, "web_search"));
        assertThat(new OpenAiWorkerProperties(null, null, 1, 1, 1, 1, 1, " ").webSearchTool()).isEqualTo("web_search");

        OpenAiWorkerProperties set = new OpenAiWorkerProperties(Map.of("itinerary", "gpt-x"), "embed-x", 100, 5, 10,
                20, 30, "web_search_preview");
        assertThat(set).isEqualTo(new OpenAiWorkerProperties(Map.of("itinerary", "gpt-x"), "embed-x", 100, 5, 10, 20,
                30, "web_search_preview"));
    }
}
