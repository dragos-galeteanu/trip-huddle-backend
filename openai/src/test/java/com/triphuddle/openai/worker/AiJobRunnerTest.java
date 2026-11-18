package com.triphuddle.openai.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedEvent;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedEvent;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.openai.client.EmbeddingResult;
import com.triphuddle.openai.client.OpenAiApiClient;
import com.triphuddle.openai.client.TextGeneration;
import com.triphuddle.openai.client.TextResult;
import com.triphuddle.openai.config.OpenAiProperties;
import com.triphuddle.openai.config.OpenAiWorkerProperties;
import com.triphuddle.openai.exception.OpenAiCallException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiJobRunnerTest {

    static final OpenAiProperties ENABLED = new OpenAiProperties(true, "sk-test", "gpt-default", "u", 0.4, 800);
    static final OpenAiWorkerProperties WORKER = new OpenAiWorkerProperties(Map.of("itinerary", "gpt-big"),
            "embed-default", 4000, 3, 1000, 5000, 180, "web_search");

    final OpenAiApiClient api = mock(OpenAiApiClient.class);
    final List<Duration> sleeps = new ArrayList<>();
    final Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    final AiJobRunner runner = runner(ENABLED, duration -> sleeps.add(duration));

    AiJobRunner runner(OpenAiProperties properties, AiJobRunner.Sleeper sleeper) {
        return new AiJobRunner(api, new ObjectMapper(), properties, WORKER, sleeper, clock);
    }

    static AiTextRequestedEvent text(String task, String schemaName, String schema, Integer maxTokens) {
        return new AiTextRequestedEvent(UUID.randomUUID(), "reply.queue", task, null, "System", "User", schemaName,
                schema, false, maxTokens);
    }

    static TextResult answer(String output) {
        return new TextResult(output, List.of(), "gpt-x", 10, 20, false, null);
    }

    static OpenAiCallException retryable(Duration retryAfter) {
        return new OpenAiCallException("busy", true, 429, retryAfter, null);
    }

    @Nested
    class Text {

        @Test
        void a_good_answer_completes_with_its_usage_and_the_tasks_model() {
            given(api.generate(any())).willReturn(answer("{\"a\": 1}"));

            AiTextCompletedEvent reply = runner.runText(text("itinerary", "plan", "{\"type\": \"object\"}", null));

            assertThat(reply.status()).isEqualTo(AiJobStatus.COMPLETED);
            assertThat(reply.output()).isEqualTo("{\"a\": 1}");
            assertThat(reply.attempts()).isEqualTo(1);
            assertThat(reply.completedAt()).isEqualTo(clock.instant());
            ArgumentCaptor<TextGeneration> sent = ArgumentCaptor.forClass(TextGeneration.class);
            then(api).should().generate(sent.capture());
            assertThat(sent.getValue().model()).isEqualTo("gpt-big");
            assertThat(sent.getValue().maxOutputTokens()).isEqualTo(4000);
        }

        @Test
        void the_model_is_the_requests_then_the_tasks_then_the_default() {
            assertThat(runner.modelFor("itinerary", "gpt-mine")).isEqualTo("gpt-mine");
            assertThat(runner.modelFor("itinerary", " ")).isEqualTo("gpt-big");
            assertThat(runner.modelFor("place-overview", null)).isEqualTo("gpt-default");
            assertThat(runner.modelFor(null, null)).isEqualTo("gpt-default");
        }

        @Test
        void a_job_that_cannot_be_sent_fails_without_calling_openai() {
            assertThat(runner(new OpenAiProperties(false, "sk", "m", "u", 0, 1), sleeps::add)
                    .runText(text("t", null, null, null)).error()).contains("disabled");
            assertThat(runner(new OpenAiProperties(true, " ", "m", "u", 0, 1), sleeps::add)
                    .runText(text("t", null, null, null)).error()).contains("disabled");
            assertThat(runner.runText(new AiTextRequestedEvent(UUID.randomUUID(), "q", "t", null, null, " ", null,
                    null, false, null)).error()).contains("user prompt");
            assertThat(runner.runText(text("t", "bad name!", "{}", null)).error()).contains("schema needs a name");
            assertThat(runner.runText(text("t", null, "{}", null)).error()).contains("schema needs a name");
            assertThat(runner.runText(text("t", "ok", "{not json", null)).error()).contains("not valid JSON");

            then(api).should(never()).generate(any());
        }

        @Test
        void a_busy_openai_is_tried_again_after_its_retry_after_or_a_growing_backoff() {
            given(api.generate(any())).willThrow(retryable(Duration.ofSeconds(3)), retryable(null))
                    .willReturn(answer("ok"));

            AiTextCompletedEvent reply = runner.runText(text("t", null, null, 100));

            assertThat(reply.status()).isEqualTo(AiJobStatus.COMPLETED);
            assertThat(reply.attempts()).isEqualTo(3);
            assertThat(sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(2));
        }

        @Test
        void a_failure_that_will_not_pass_or_the_last_attempt_ends_the_job() {
            given(api.generate(any())).willThrow(new OpenAiCallException("bad key", false, 401, null, null));
            assertThat(runner.runText(text("t", null, null, null)).error()).isEqualTo("bad key");

            willThrow(retryable(null)).given(api).generate(any());
            AiTextCompletedEvent reply = runner.runText(text("t", null, null, null));
            assertThat(reply.status()).isEqualTo(AiJobStatus.FAILED);
            assertThat(reply.attempts()).isEqualTo(3);
        }

        @Test
        void a_refusal_fails_at_once_and_counts_its_tokens() {
            given(api.generate(any())).willReturn(new TextResult(null, List.of(), "gpt-x", 5, 6, false, "Not this"));

            AiTextCompletedEvent reply = runner.runText(text("t", null, null, null));

            assertThat(reply.error()).isEqualTo("The model refused: Not this");
            assertThat(reply.inputTokens() + reply.outputTokens()).isEqualTo(11);
        }

        @Test
        void an_answer_cut_short_is_tried_with_more_room_up_to_four_times_the_request() {
            given(api.generate(any())).willReturn(new TextResult(null, List.of(), "m", 1, 1, true, null));

            AiTextCompletedEvent reply = runner.runText(text("t", null, null, 1000));

            ArgumentCaptor<TextGeneration> sent = ArgumentCaptor.forClass(TextGeneration.class);
            then(api).should(times(3)).generate(sent.capture());
            assertThat(sent.getAllValues()).extracting(TextGeneration::maxOutputTokens)
                    .containsExactly(1000, 2000, 4000);
            assertThat(reply.error()).contains("cut short at 4000");
            assertThat(reply.inputTokens()).isEqualTo(3);
        }

        @Test
        void an_answer_that_is_not_the_json_asked_for_is_tried_again() {
            given(api.generate(any())).willReturn(answer("plain words"), answer("\"a string\""), answer("[1]"));

            AiTextCompletedEvent reply = runner.runText(text("t", "list", "{\"type\": \"array\"}", null));

            assertThat(reply.status()).isEqualTo(AiJobStatus.COMPLETED);
            assertThat(reply.attempts()).isEqualTo(3);
        }

        @Test
        void an_interrupted_wait_ends_the_job_and_keeps_the_interrupt() {
            given(api.generate(any())).willThrow(retryable(null));
            AiJobRunner interrupted = runner(ENABLED, duration -> {
                throw new InterruptedException();
            });

            assertThat(interrupted.runText(text("t", null, null, null)).attempts()).isEqualTo(1);
            assertThat(Thread.interrupted()).isTrue();

            willReturn(answer("not json")).given(api).generate(any());
            assertThat(interrupted.runText(text("t", "s", "{}", null)).attempts()).isEqualTo(1);
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Nested
    class Embeddings {

        static AiEmbeddingRequestedEvent embed(String model, List<String> inputs) {
            return new AiEmbeddingRequestedEvent(UUID.randomUUID(), "reply.queue", "place-embedding", model, inputs);
        }

        @Test
        void inputs_are_embedded_with_the_requested_or_default_model() {
            given(api.embed(anyString(), anyList())).willReturn(new EmbeddingResult(List.of(new float[] {1f}), "e",
                    3));

            AiEmbeddingCompletedEvent reply = runner.runEmbedding(embed(null, List.of("Lisbon")));
            runner.runEmbedding(embed("embed-mine", List.of("Porto")));

            assertThat(reply.status()).isEqualTo(AiJobStatus.COMPLETED);
            assertThat(reply.inputTokens()).isEqualTo(3);
            then(api).should().embed(eq("embed-default"), anyList());
            then(api).should().embed(eq("embed-mine"), anyList());
        }

        @Test
        void bad_inputs_or_a_disabled_worker_fail_without_calling_openai() {
            List<String> tooMany = Collections.nCopies(AiEmbeddingRequestedEvent.MAX_INPUTS + 1, "x");
            for (List<String> inputs : List.of(List.<String>of(), tooMany, java.util.Arrays.asList("a", null),
                    List.of(" "))) {
                assertThat(runner.runEmbedding(embed(null, inputs)).status()).isEqualTo(AiJobStatus.FAILED);
            }
            assertThat(runner.runEmbedding(embed(null, null)).status()).isEqualTo(AiJobStatus.FAILED);
            assertThat(runner(new OpenAiProperties(false, "sk", "m", "u", 0, 1), sleeps::add)
                    .runEmbedding(embed(null, List.of("a"))).error()).contains("disabled");
            then(api).should(never()).embed(anyString(), anyList());
        }

        @Test
        void a_busy_openai_is_retried_and_a_lasting_failure_reported() {
            given(api.embed(anyString(), anyList())).willThrow(retryable(null))
                    .willReturn(new EmbeddingResult(List.of(new float[] {1f}), "e", 1));
            assertThat(runner.runEmbedding(embed(null, List.of("a"))).status()).isEqualTo(AiJobStatus.COMPLETED);

            willThrow(new OpenAiCallException("bad", false)).given(api).embed(anyString(), anyList());
            assertThat(runner.runEmbedding(embed(null, List.of("a"))).error()).isEqualTo("bad");
        }
    }

    @Nested
    class Listener {

        final AiJobRunner jobs = mock(AiJobRunner.class);
        final RabbitMQMessageProducer producer = mock(RabbitMQMessageProducer.class);
        final AiWorkerListener listener = new AiWorkerListener(jobs, producer, Set.of("reply.queue"));

        @Test
        void answers_go_to_the_reply_queue_the_job_names_if_it_is_a_known_one() {
            AiTextRequestedEvent known = text("t", null, null, null);
            AiTextCompletedEvent done = new AiTextCompletedEvent(known.jobId(), "t", AiJobStatus.COMPLETED, "x",
                    List.of(), "m", 1, 1, 1, null, Instant.now());
            given(jobs.runText(known)).willReturn(done);
            AiEmbeddingRequestedEvent embedding = Embeddings.embed(null, List.of("a"));
            AiEmbeddingCompletedEvent embedded = new AiEmbeddingCompletedEvent(embedding.jobId(), "e",
                    AiJobStatus.COMPLETED, List.of(), "m", 1, null, Instant.now());
            given(jobs.runEmbedding(embedding)).willReturn(embedded);

            listener.onTextRequested(known);
            listener.onEmbeddingRequested(embedding);
            listener.onTextRequested(new AiTextRequestedEvent(UUID.randomUUID(), "elsewhere", "t", null, null, "u",
                    null, null, false, null));
            listener.onEmbeddingRequested(new AiEmbeddingRequestedEvent(UUID.randomUUID(), null, "e", null,
                    List.of("a")));

            then(producer).should().publish(done, "", "reply.queue");
            then(producer).should().publish(embedded, "", "reply.queue");
            then(producer).shouldHaveNoMoreInteractions();
        }
    }
}
