package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiCitation;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.api.destination.job.ContentJobs;
import com.triphuddle.api.destination.messaging.AiReplyListener;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DST-5: AI content written once per place, section and language, shared, refreshed and kept within budgets. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ContentIT extends DestinationApiSupport {

    static final String OVERVIEW = "{\"summary\": \"A city of hills\"}";

    @Autowired AiReplyListener aiReplies;
    @Autowired ContentJobs contentJobs;

    UUID lisbon;
    UUID reader;

    @BeforeEach
    void lisbon() throws Exception {
        country("PT");
        UUID region = idOf(place("PT", "REGION", "Lisbon District", null));
        lisbon = idOf(place("PT", "CITY", "Lisbon", region));
        reader = subscriber();
    }

    String content(String query) {
        return PLACES + "/" + lisbon + "/content" + query;
    }

    JsonNode read(String query, UUID user) throws Exception {
        return body(call(get(content(query)), Caller.CUSTOMER, user).andExpect(status().isOk()));
    }

    List<AiTextRequestedEvent> requested() {
        return published.published().stream().map(p -> p.payload())
                .filter(AiTextRequestedEvent.class::isInstance).map(AiTextRequestedEvent.class::cast).toList();
    }

    AiTextRequestedEvent lastRequested() {
        List<AiTextRequestedEvent> all = requested();
        return all.get(all.size() - 1);
    }

    void answer(AiTextRequestedEvent job, String output) {
        aiReplies.onTextCompleted(new AiTextCompletedEvent(job.jobId(), job.task(), AiJobStatus.COMPLETED, output,
                List.of(new AiCitation("https://visitlisboa.example", "Visit Lisboa")), "gpt-test", 100, 200, 1, null,
                Instant.now()));
    }

    void fail(AiTextRequestedEvent job, String error) {
        aiReplies.onTextCompleted(new AiTextCompletedEvent(job.jobId(), job.task(), AiJobStatus.FAILED, null, null,
                "gpt-test", 10, 0, 3, error, Instant.now()));
    }

    void spent(long tokens) {
        jdbc.update("insert into destination_ai_usage (day, input_tokens, output_tokens, jobs) values (?, ?, 0, 1) "
                + "on conflict (day) do update set input_tokens = destination_ai_usage.input_tokens + excluded.input_tokens",
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), tokens);
    }

    @Nested
    class Writing_once_and_sharing {

        @Test
        void the_first_reader_starts_the_writing_and_everyone_then_shares_it() throws Exception {
            JsonNode first = read("?sections=OVERVIEW", reader);
            assertThat(first.get(0).get("state").asText()).isEqualTo("GENERATING");
            assertThat(requested()).hasSize(1);
            assertThat(lastRequested().task()).isEqualTo("place-overview");
            assertThat(lastRequested().userPrompt()).contains("Lisbon", "Lisbon District");

            read("?sections=OVERVIEW", subscriber());
            assertThat(requested()).hasSize(1);

            answer(lastRequested(), OVERVIEW);
            JsonNode ready = read("?sections=OVERVIEW", subscriber()).get(0);
            assertThat(ready.get("state").asText()).isEqualTo("READY");
            assertThat(ready.get("content").get("summary").asText()).isEqualTo("A city of hills");
            assertThat(ready.get("citations").get(0).get("url").asText()).isEqualTo("https://visitlisboa.example");
            assertThat(ready.get("isAiGenerated").asBoolean()).isTrue();
        }

        @Test
        void every_section_is_asked_for_when_none_is_named() throws Exception {
            assertThat(read("", reader)).hasSize(3);

            assertThat(requested()).extracting(AiTextRequestedEvent::task)
                    .containsExactlyInAnyOrder("place-overview", "place-opinions", "place-practical");
        }

        @Test
        void another_language_is_written_on_its_own_with_english_standing_in_meanwhile() throws Exception {
            read("?sections=OVERVIEW", reader);
            answer(lastRequested(), OVERVIEW);

            JsonNode portuguese = body(call(get(content("?sections=OVERVIEW")).header("Accept-Language",
                    "pt-PT,pt;q=0.9"), Caller.CUSTOMER, reader)).get(0);

            assertThat(portuguese.get("isFallback").asBoolean()).isTrue();
            assertThat(portuguese.get("languageCode").asText()).isEqualTo("en");
            assertThat(lastRequested().systemPrompt()).isNotBlank();
            assertThat(requested()).hasSize(2);
            assertThat(read("?sections=OVERVIEW&language=nonsense!", reader).get(0).get("languageCode").asText())
                    .isEqualTo("en");
        }
    }

    @Nested
    class Failures_and_refreshes {

        @Test
        void a_failed_or_malformed_answer_is_retried_only_after_a_while() throws Exception {
            read("?sections=OVERVIEW", reader);
            fail(lastRequested(), "rate limited");
            assertThat(read("?sections=OVERVIEW", reader).get(0).get("state").asText()).isEqualTo("FAILED");
            assertThat(requested()).hasSize(1);

            clock.advance(Duration.ofMinutes(61));
            read("?sections=OVERVIEW", reader);
            assertThat(requested()).hasSize(2);
            answer(lastRequested(), "[1, 2]");
            clock.advance(Duration.ofMinutes(61));
            read("?sections=OVERVIEW", reader);
            answer(lastRequested(), "not json");
            clock.advance(Duration.ofMinutes(61));
            read("?sections=OVERVIEW", reader);
            answer(lastRequested(), OVERVIEW);
            assertThat(read("?sections=OVERVIEW", reader).get(0).get("state").asText()).isEqualTo("READY");
        }

        @Test
        void aged_content_is_still_served_while_it_is_refreshed() throws Exception {
            read("?sections=OPINIONS", reader);
            answer(lastRequested(), "{\"opinions\": \"old\"}");
            clock.advance(Duration.ofDays(31));

            JsonNode stale = read("?sections=OPINIONS", reader).get(0);
            assertThat(stale.get("state").asText()).isEqualTo("READY");
            assertThat(stale.get("isRefreshing").asBoolean()).isTrue();

            fail(lastRequested(), "down");
            assertThat(read("?sections=OPINIONS", reader).get(0).get("content").get("opinions").asText())
                    .isEqualTo("old");
        }

        @Test
        void a_late_answer_to_a_job_that_was_resent_is_ignored() throws Exception {
            read("?sections=OVERVIEW", reader);
            AiTextRequestedEvent lost = lastRequested();
            clock.advance(Duration.ofMinutes(31));

            contentJobs.run();
            AiTextRequestedEvent resent = lastRequested();
            assertThat(resent.jobId()).isNotEqualTo(lost.jobId());

            answer(lost, "{\"summary\": \"late\"}");
            assertThat(read("?sections=OVERVIEW", reader).get(0).get("state").asText()).isEqualTo("GENERATING");
            answer(resent, OVERVIEW);
            assertThat(read("?sections=OVERVIEW", reader).get(0).get("state").asText()).isEqualTo("READY");
        }

        @Test
        void content_read_lately_is_refreshed_in_the_background_once_it_ages() throws Exception {
            read("?sections=OPINIONS", reader);
            answer(lastRequested(), "{\"opinions\": \"Lovely\"}");
            clock.advance(Duration.ofDays(29));
            read("?sections=OPINIONS", reader);
            clock.advance(Duration.ofDays(2));
            published.reset();

            contentJobs.run();

            assertThat(requested()).hasSize(1);
        }
    }

    @Nested
    class Budgets {

        @Test
        void the_daily_budget_stops_refreshes_and_the_hard_limit_stops_new_writing() throws Exception {
            // A year's license, as the clock moves past the content's six months.
            reader = UUID.randomUUID();
            license(com.triphuddle.amqp.license.LicenseTarget.USER, reader, Map.of(ACCESS, "true"),
                    Instant.now().plus(Duration.ofDays(365)));
            read("?sections=OVERVIEW", reader);
            answer(lastRequested(), OVERVIEW);
            clock.advance(Duration.ofDays(181));
            spent(2_000_000);
            published.reset();

            assertThat(read("?sections=OVERVIEW", reader).get(0).get("state").asText()).isEqualTo("READY");
            assertThat(requested()).isEmpty();
            read("?sections=OPINIONS", reader);
            assertThat(requested()).hasSize(1);

            spent(3_000_000);
            assertThat(read("?sections=PRACTICAL", reader).get(0).get("state").asText()).isEqualTo("UNAVAILABLE");
            assertThat(requested()).hasSize(1);
        }

        @Test
        void a_reader_has_a_daily_share_of_new_writing() throws Exception {
            jdbc.update("insert into destination_ai_user_usage (user_id, day, generations) values (?, ?, 30)", reader,
                    LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));

            assertThat(read("?sections=OVERVIEW", reader).get(0).get("state").asText()).isEqualTo("UNAVAILABLE");
            assertThat(read("?sections=OVERVIEW", subscriber()).get(0).get("state").asText()).isEqualTo("GENERATING");
        }
    }

    @Nested
    class Staff {

        @Test
        void staff_regenerate_content_and_review_readers_reports() throws Exception {
            read("?sections=OVERVIEW", reader);
            answer(lastRequested(), OVERVIEW);
            call(post(PLACES + "/" + lisbon + "/content/OPINIONS/report"), Caller.CUSTOMER, reader,
                    Map.of("reason", "Nothing yet")).andExpect(status().isNotFound());

            UUID report = idOf(body(call(post(PLACES + "/" + lisbon + "/content/OVERVIEW/report"), Caller.CUSTOMER,
                    reader, Map.of("reason", "  Wrong hills  ")).andExpect(status().isCreated())));

            JsonNode open = body(call(get("/api/v1/destinations/content-reports"), Caller.INSPECTOR,
                    UUID.randomUUID()).andExpect(status().isOk()));
            assertThat(ids(open.get("data"))).containsExactly(report.toString());
            assertThat(open.get("data").get(0).get("reason").asText()).isEqualTo("Wrong hills");
            published.reset();
            call(post("/api/v1/destinations/content-reports/" + report + "/resolve?isRegenerating=true"),
                    Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolution").value("REGENERATED"));
            assertThat(requested()).hasSize(1);
            call(post("/api/v1/destinations/content-reports/" + report + "/resolve?isRegenerating=false"),
                    Caller.ADMIN, UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
            assertThat(body(call(get("/api/v1/destinations/content-reports?isOpen=false"), Caller.SUPPORT,
                    UUID.randomUUID())).get("data")).hasSize(1);
            call(post("/api/v1/destinations/content-reports/" + UUID.randomUUID() + "/resolve?isRegenerating=false"),
                    Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        void a_regeneration_is_asked_for_each_named_section() throws Exception {
            call(post(PLACES + "/" + lisbon + "/content/regenerate"), Caller.SUPPORT, UUID.randomUUID(),
                    Map.of("language", "de", "sections", List.of("OVERVIEW", "PRACTICAL")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].state").value("GENERATING"));
            spent(2_000_000);
            call(post(PLACES + "/" + lisbon + "/content/regenerate"), Caller.SUPPORT, UUID.randomUUID(),
                    Map.of("sections", List.of("OPINIONS")))
                    .andExpect(jsonPath("$[0].state").value("UNAVAILABLE"));
            call(post(PLACES + "/" + lisbon + "/content/regenerate"), Caller.CUSTOMER, reader, Map.of())
                    .andExpect(status().isForbidden());
        }
    }
}
