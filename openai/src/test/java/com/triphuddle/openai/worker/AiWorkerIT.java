package com.triphuddle.openai.worker;

import com.sun.net.httpserver.HttpServer;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedContract;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedEvent;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedContract;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedEvent;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedContract;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedContract;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/** The worker's round trip through a real RabbitMQ: a job in, OpenAI (faked over HTTP) called, the answer out. */
@SpringBootTest
@ActiveProfiles("it")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiWorkerIT {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    static final HttpServer OPENAI;
    // Answers in order: each request takes the next, and the last repeats.
    static final Queue<String[]> ANSWERS = new ConcurrentLinkedQueue<>();
    static volatile String[] last = {"500", "{}"};

    static {
        RABBIT.start();
        try {
            OPENAI = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        OPENAI.createContext("/v1", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String[] answer = ANSWERS.isEmpty() ? last : ANSWERS.poll();
            last = answer;
            byte[] body = answer[1].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(Integer.parseInt(answer[0]), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        OPENAI.start();
    }

    @DynamicPropertySource
    static void outside(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("integration.openai.base-url", () -> "http://localhost:" + OPENAI.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void stop() {
        OPENAI.stop(0);
    }

    @Autowired @Qualifier("amqpTemplate") AmqpTemplate amqp;

    static void answers(String[]... answers) {
        ANSWERS.clear();
        ANSWERS.addAll(List.of(answers));
    }

    @Test
    void a_text_job_is_answered_on_the_requesters_queue() {
        answers(new String[] {"429", "{\"error\": {\"message\": \"Slow down\"}}"}, new String[] {"200", """
                {"model": "gpt-test", "status": "completed", "usage": {"input_tokens": 3, "output_tokens": 4},
                 "output": [{"type": "message", "content": [{"type": "output_text", "text": "{\\"ok\\": true}"}]}]}"""});
        UUID jobId = UUID.randomUUID();

        amqp.convertAndSend("", AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME, new AiTextRequestedEvent(jobId,
                AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME, "place-overview", null, "System",
                "Lisbon", "overview", "{\"type\": \"object\"}", true, 500));

        AiTextCompletedEvent reply = (AiTextCompletedEvent) amqp.receiveAndConvert(
                AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME, 20_000);
        assertThat(reply.jobId()).isEqualTo(jobId);
        assertThat(reply.status()).isEqualTo(AiJobStatus.COMPLETED);
        assertThat(reply.output()).isEqualTo("{\"ok\": true}");
        assertThat(reply.attempts()).isEqualTo(2);
    }

    @Test
    void an_embedding_job_is_answered_and_a_bad_key_reported_as_failed() {
        answers(new String[] {"200", """
                {"model": "embed-test", "usage": {"prompt_tokens": 2},
                 "data": [{"index": 0, "embedding": [0.1, 0.2]}]}"""},
                new String[] {"401", "{\"error\": {\"message\": \"Incorrect API key\"}}"});
        String replies = AiEmbeddingCompletedContract.AI_EMBEDDING_COMPLETED_DESTINATION_QUEUE_NAME;

        amqp.convertAndSend("", AiEmbeddingRequestedContract.AI_EMBEDDING_REQUESTED_QUEUE_NAME,
                new AiEmbeddingRequestedEvent(UUID.randomUUID(), replies, "place-embedding", null, List.of("Lisbon")));
        AiEmbeddingCompletedEvent embedded = (AiEmbeddingCompletedEvent) amqp.receiveAndConvert(replies, 20_000);
        amqp.convertAndSend("", AiEmbeddingRequestedContract.AI_EMBEDDING_REQUESTED_QUEUE_NAME,
                new AiEmbeddingRequestedEvent(UUID.randomUUID(), replies, "place-embedding", null, List.of("Porto")));
        AiEmbeddingCompletedEvent refused = (AiEmbeddingCompletedEvent) amqp.receiveAndConvert(replies, 20_000);

        assertThat(embedded.status()).isEqualTo(AiJobStatus.COMPLETED);
        assertThat(embedded.vectors().get(0)).containsExactly(0.1f, 0.2f);
        assertThat(refused.status()).isEqualTo(AiJobStatus.FAILED);
        assertThat(refused.error()).contains("Incorrect API key");
    }
}
