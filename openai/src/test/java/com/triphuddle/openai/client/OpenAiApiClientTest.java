package com.triphuddle.openai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.ai.AiCitation;
import com.triphuddle.openai.config.OpenAiProperties;
import com.triphuddle.openai.config.OpenAiWorkerProperties;
import com.triphuddle.openai.dto.GenerateTextRequest;
import com.triphuddle.openai.exception.OpenAiCallException;
import com.triphuddle.openai.exception.OpenAiIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pins what the client sends to OpenAI and how it reads the answers and errors, against mocked HTTP. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OpenAiApiClientTest {

    static final OpenAiProperties PROPERTIES = new OpenAiProperties(true, "sk-test", "gpt-test",
            "https://openai.example/v1", 0.4, 800);
    static final OpenAiWorkerProperties WORKER = new OpenAiWorkerProperties(Map.of(), "embed-test", 4000, 3, 10, 50,
            180, "web_search");

    MockRestServiceServer server;
    OpenAiApiClient client;
    OpenAiChatCompletionsTextGenerator chat;

    @BeforeEach
    void client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openai.example/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new OpenAiApiClient(restClient, new ObjectMapper(), PROPERTIES, WORKER);
        chat = new OpenAiChatCompletionsTextGenerator(restClient, new ObjectMapper(), PROPERTIES);
    }

    @Test
    void a_generation_asks_for_strict_json_with_web_search_and_stores_nothing() throws Exception {
        server.expect(requestTo("https://openai.example/v1/responses"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.instructions").value("Be brief"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.max_output_tokens").value(500))
                .andExpect(jsonPath("$.tools[0].type").value("web_search"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.name").value("answer"))
                .andRespond(withSuccess("""
                        {"model": "gpt-test-2026", "status": "completed",
                         "usage": {"input_tokens": 12, "output_tokens": 34},
                         "output": [
                           {"type": "web_search_call"},
                           {"type": "message", "content": [
                             {"type": "output_text", "text": "{\\"a\\":", "annotations": [
                               {"type": "url_citation", "url": "https://a.example", "title": "A"},
                               {"type": "url_citation", "url": "https://a.example", "title": "A again"},
                               {"type": "file_citation"}]},
                             {"type": "output_text", "text": " 1}", "annotations": [
                               {"type": "url_citation", "url": "https://b.example", "title": null}]}]}]}""",
                        MediaType.APPLICATION_JSON));

        TextResult result = client.generate(new TextGeneration("gpt-test", "Be brief", "Hi", "answer",
                new ObjectMapper().readTree("{\"type\": \"object\"}"), true, 500));

        assertThat(result.output()).isEqualTo("{\"a\": 1}");
        assertThat(result.citations()).containsExactly(new AiCitation("https://a.example", "A"),
                new AiCitation("https://b.example", null));
        assertThat(result.model()).isEqualTo("gpt-test-2026");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(34);
        assertThat(result.isIncomplete()).isFalse();
    }

    @Test
    void a_plain_generation_sends_no_tools_schema_or_instructions_and_reads_a_refusal_or_a_cut() {
        server.expect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.instructions").doesNotExist())
                .andExpect(jsonPath("$.text").doesNotExist())
                .andRespond(withSuccess("""
                        {"status": "completed", "output": [{"type": "message", "content": [
                          {"type": "refusal", "refusal": "No"}]}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(withSuccess(
                "{\"status\": \"incomplete\", \"output\": []}", MediaType.APPLICATION_JSON));

        TextResult refused = client.generate(new TextGeneration("m", " ", "Hi", null, null, false, 100));
        TextResult cut = client.generate(new TextGeneration("m", null, "Hi", null, null, false, 100));

        assertThat(refused.refusal()).isEqualTo("No");
        assertThat(refused.output()).isNull();
        assertThat(cut.isIncomplete()).isTrue();
    }

    @Test
    void rate_limits_and_server_errors_may_be_retried_but_a_bad_request_never() {
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "1.5")
                        .body("{\"error\": {\"message\": \"Slow down\"}}"));
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(
                withStatus(HttpStatus.BAD_REQUEST).body("not json"));
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(
                withStatus(HttpStatus.BAD_GATEWAY).header("Retry-After", "soon").body("{}"));
        server.expect(requestTo("https://openai.example/v1/responses"))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));
        TextGeneration generation = new TextGeneration("m", null, "Hi", null, null, false, 100);

        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class, e -> {
            assertThat(e.isRetryable()).isTrue();
            assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMillis(1500));
            assertThat(e.getMessage()).isEqualTo("OpenAI answered 429: Slow down");
        });
        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class, e -> {
            assertThat(e.isRetryable()).isFalse();
            assertThat(e.getMessage()).endsWith("not json");
        });
        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class, e -> {
            assertThat(e.isRetryable()).isTrue();
            assertThat(e.getRetryAfter()).isNull();
        });
        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class,
                e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void a_timeout_or_conflict_answer_may_be_retried_and_an_unexpected_failure_is_retried_too() {
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(
                withStatus(HttpStatus.REQUEST_TIMEOUT).body("{\"error\": {\"message\": \"Took too long\"}}"));
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(
                withStatus(HttpStatus.CONFLICT).body("{}"));
        server.expect(requestTo("https://openai.example/v1/responses")).andRespond(request -> {
            throw new IllegalStateException("connection reset by a proxy");
        });
        TextGeneration generation = new TextGeneration("m", null, "Hi", null, null, false, 100);

        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class, e -> {
            assertThat(e.isRetryable()).isTrue();
            assertThat(e.getMessage()).isEqualTo("OpenAI answered 408: Took too long");
        });
        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class,
                e -> assertThat(e.isRetryable()).isTrue());
        assertThatThrownBy(() -> client.generate(generation)).isInstanceOfSatisfying(OpenAiCallException.class, e -> {
            assertThat(e.isRetryable()).isTrue();
            assertThat(e.getMessage()).isEqualTo("Calling OpenAI failed: connection reset by a proxy");
        });
    }

    @Test
    void embeddings_come_back_in_the_inputs_order() {
        server.expect(requestTo("https://openai.example/v1/embeddings"))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                .andExpect(jsonPath("$.input[1]").value("b"))
                .andRespond(withSuccess("""
                        {"model": "embed-test", "usage": {"prompt_tokens": 7}, "data": [
                          {"index": 1, "embedding": [0.5, 0.25]}, {"index": 0, "embedding": [1, 0]}]}""",
                        MediaType.APPLICATION_JSON));

        EmbeddingResult result = client.embed("embed-test", List.of("a", "b"));

        assertThat(result.vectors().get(0)).containsExactly(1f, 0f);
        assertThat(result.vectors().get(1)).containsExactly(0.5f, 0.25f);
        assertThat(result.inputTokens()).isEqualTo(7);
    }

    @Test
    void an_embedding_for_an_unknown_or_missing_input_fails_the_call() {
        server.expect(requestTo("https://openai.example/v1/embeddings")).andRespond(withSuccess(
                "{\"data\": [{\"index\": 5, \"embedding\": []}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openai.example/v1/embeddings")).andRespond(withSuccess(
                "{\"data\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("m", List.of("a"))).isInstanceOfSatisfying(OpenAiCallException.class,
                e -> assertThat(e.isRetryable()).isFalse());
        assertThatThrownBy(() -> client.embed("m", List.of("a"))).isInstanceOfSatisfying(OpenAiCallException.class,
                e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void the_chat_generator_sends_context_and_current_text_and_returns_the_trimmed_answer() {
        server.expect(requestTo("https://openai.example/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Context:\nA trip"),
                        org.hamcrest.Matchers.containsString("Current description:\nOld"))))
                .andRespond(withSuccess("{\"choices\": [{\"message\": {\"content\": \"  New  \"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openai.example/v1/chat/completions")).andRespond(withSuccess(
                "{\"choices\": []}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openai.example/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(chat.generateText(new GenerateTextRequest(null, "Write", "Old", "A trip"))).isEqualTo("New");
        assertThatThrownBy(() -> chat.generateText(new GenerateTextRequest("S", "Write", null, null)))
                .isInstanceOf(OpenAiIntegrationException.class).hasMessageContaining("did not return");
        assertThatThrownBy(() -> chat.generateText(new GenerateTextRequest("S", "Write", null, null)))
                .hasMessageContaining("Failed to generate");
        assertThatThrownBy(() -> chat.generateText(new GenerateTextRequest("S", " ", null, null)))
                .hasMessageContaining("user prompt");
        assertThatThrownBy(() -> new OpenAiChatCompletionsTextGenerator(RestClient.create(), new ObjectMapper(),
                new OpenAiProperties(true, " ", "m", "u", 0, 1)).generateText(
                new GenerateTextRequest(null, "Write", null, null))).hasMessageContaining("API key");
        assertThatThrownBy(() -> new DisabledAiTextGenerator().generateText(null)).hasMessageContaining("disabled");
    }
}
