package com.triphuddle.openai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.openai.config.OpenAiProperties;
import com.triphuddle.openai.dto.GenerateTextRequest;
import com.triphuddle.openai.exception.OpenAiIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The chat-completions text generator: what it sends OpenAI, and each way an answer can fail it.
 */
class OpenAiChatCompletionsTextGeneratorTest {

    private static final String URL = "https://openai.example/v1/chat/completions";
    private static final OpenAiProperties PROPERTIES = new OpenAiProperties(true, "sk-test", "gpt-test",
            "https://openai.example/v1", 0.4, 300);

    private MockRestServiceServer server;
    private OpenAiChatCompletionsTextGenerator generator;

    @BeforeEach
    void generator() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openai.example/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new OpenAiChatCompletionsTextGenerator(builder.build(), new ObjectMapper(), PROPERTIES);
    }

    @Test
    void sendsTheConfiguredModelAndLimitsWithTheKeyAndADefaultSystemPromptAndOnlyTheTask() {
        server.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.temperature").value(0.4))
                .andExpect(jsonPath("$.max_tokens").value(300))
                .andExpect(jsonPath("$.messages[0].content").value(startsWith("You are a professional writing")))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content")
                        .value("Task:\nWrite a summary\n\nReturn only the final description text."))
                .andRespond(withSuccess("{\"choices\": [{\"message\": {\"content\": \"Done\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(generator.generateText(new GenerateTextRequest("  ", " Write a summary ", " ", " ")))
                .isEqualTo("Done");
        server.verify();
    }

    @Test
    void aGivenSystemPromptIsSentAsItIs() {
        server.expect(requestTo(URL)).andExpect(jsonPath("$.messages[0].content").value("Be brief."))
                .andRespond(withSuccess("{\"choices\": [{\"message\": {\"content\": \"Ok\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(generator.generateText(new GenerateTextRequest("Be brief.", "Write", null, null))).isEqualTo("Ok");
    }

    @ParameterizedTest(name = "an answer of {0} fails the generation")
    @ValueSource(strings = {"", "{\"choices\": [{\"message\": {}}]}",
            "{\"choices\": [{\"message\": {\"content\": \"   \"}}]}"})
    void anEmptyAnswerOrOneWithoutContentFails(String answer) {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> generator.generateText(new GenerateTextRequest(null, "Write", null, null)))
                .isInstanceOf(OpenAiIntegrationException.class)
                .hasMessageMatching("OpenAI (returned an empty response|did not return generated content)\\.");
    }

    @Test
    void anAnswerThatIsNotJsonFailsWithItsCause() {
        server.expect(requestTo(URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> generator.generateText(new GenerateTextRequest(null, "Write", null, null)))
                .isInstanceOf(OpenAiIntegrationException.class).hasMessage("Failed to generate text via OpenAI.")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void noRequestNoPromptOrNoKeyIsRefusedBeforeCallingOpenAi() {
        assertThatThrownBy(() -> generator.generateText(null)).hasMessageContaining("user prompt");
        assertThatThrownBy(() -> generator.generateText(new GenerateTextRequest("S", null, null, null)))
                .hasMessageContaining("user prompt");
        assertThatThrownBy(() -> new OpenAiChatCompletionsTextGenerator(RestClient.create(), new ObjectMapper(),
                new OpenAiProperties(true, null, "m", "u", 0, 1))
                .generateText(new GenerateTextRequest(null, "Write", null, null))).hasMessageContaining("API key");
        server.verify();
    }
}
