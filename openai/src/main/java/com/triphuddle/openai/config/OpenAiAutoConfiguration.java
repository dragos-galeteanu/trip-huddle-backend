package com.triphuddle.openai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiQueues;
import com.triphuddle.openai.api.AiTextGenerator;
import com.triphuddle.openai.client.DisabledAiTextGenerator;
import com.triphuddle.openai.client.OpenAiApiClient;
import com.triphuddle.openai.client.OpenAiChatCompletionsTextGenerator;
import com.triphuddle.openai.worker.AiJobRunner;
import com.triphuddle.openai.worker.AiWorkerListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;

/**
 * Wires the {@link AiTextGenerator}, the OpenAI client when enabled and a refusing stand-in otherwise, and the
 * worker that runs other services' AI jobs from amqp.
 */
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, OpenAiWorkerProperties.class})
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
     * The client for OpenAI's Responses and embeddings endpoints, with the worker's timeouts.
     *
     * @param restClientBuilder the builder for the HTTP client
     * @param objectMapper the JSON mapper
     * @param properties the OpenAI settings
     * @param workerProperties the worker settings
     * @return the client
     */
    @Bean
    public OpenAiApiClient openAiApiClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                           OpenAiProperties properties, OpenAiWorkerProperties workerProperties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(workerProperties.readTimeoutSeconds()));
        RestClient restClient = restClientBuilder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new OpenAiApiClient(restClient, objectMapper, properties, workerProperties);
    }

    /**
     * Runs AI jobs: checks, retries and model choice.
     *
     * @param apiClient the OpenAI client
     * @param objectMapper the JSON mapper
     * @param properties the OpenAI settings
     * @param workerProperties the worker settings
     * @return the job runner
     */
    @Bean
    public AiJobRunner aiJobRunner(OpenAiApiClient apiClient, ObjectMapper objectMapper, OpenAiProperties properties,
                                   OpenAiWorkerProperties workerProperties) {
        return new AiJobRunner(apiClient, objectMapper, properties, workerProperties,
                duration -> Thread.sleep(duration.toMillis()), Clock.systemUTC());
    }

    /**
     * Takes AI jobs off amqp and answers them on the reply queues {@link AiQueues} lists.
     *
     * @param jobRunner the job runner
     * @param messageProducer the publisher
     * @return the listener
     */
    @Bean
    public AiWorkerListener aiWorkerListener(AiJobRunner jobRunner, RabbitMQMessageProducer messageProducer) {
        return new AiWorkerListener(jobRunner, messageProducer, new HashSet<>(AiQueues.REPLY_QUEUE_NAMES));
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
