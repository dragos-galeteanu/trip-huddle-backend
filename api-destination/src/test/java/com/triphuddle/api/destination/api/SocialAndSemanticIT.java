package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedEvent;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedEvent;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.api.destination.job.ContentJobs;
import com.triphuddle.api.destination.messaging.AiReplyListener;
import com.triphuddle.api.destination.messaging.UserEventListener;
import com.triphuddle.api.destination.semantic.SemanticSearchService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DST-8 (favourites, visits, reviews, questions), DST-9 (semantic search) and DST-10 (forgetting a user). */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SocialAndSemanticIT extends DestinationApiSupport {

    static final String API = "/api/v1/destinations";
    static final int DIMENSIONS = 1536;

    @Autowired AiReplyListener aiReplies;
    @Autowired UserEventListener userEvents;
    @Autowired ContentJobs contentJobs;
    @Autowired SemanticSearchService semantic;

    UUID lisbon;
    UUID porto;
    UUID ana;

    @BeforeEach
    void places() throws Exception {
        country("PT");
        lisbon = idOf(place("PT", "CITY", "Lisbon", null, "tags", List.of("coast")));
        porto = idOf(place("PT", "CITY", "Porto", null));
        ana = subscriber();
    }

    JsonNode review(UUID place, UUID user, int rating) throws Exception {
        return body(call(put(PLACES + "/" + place + "/reviews/mine"), Caller.CUSTOMER, user,
                Map.of("rating", rating, "title", " Lovely ", "body", " ", "language", "PT"))
                .andExpect(status().isOk()));
    }

    void report(String reviewId, UUID user, int expected) throws Exception {
        call(post(API + "/reviews/" + reviewId + "/report"), Caller.CUSTOMER, user, Map.of("reason", " Rude "))
                .andExpect(status().is(expected));
    }

    @Nested
    class Marks {

        @Test
        void favourites_and_visits_are_private_and_lifted_at_will() throws Exception {
            call(put(PLACES + "/" + lisbon + "/favourite"), Caller.CUSTOMER, ana)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("FAVOURITE"))
                    .andExpect(jsonPath("$.markedAt").exists());
            call(put(PLACES + "/" + porto + "/visited"), Caller.CUSTOMER, ana,
                    Map.of("visitedOn", LocalDate.now().minusDays(3)))
                    .andExpect(jsonPath("$.visitedOn").value(LocalDate.now().minusDays(3).toString()));
            call(put(PLACES + "/" + porto + "/visited"), Caller.CUSTOMER, ana,
                    Map.of("visitedOn", LocalDate.now().plusDays(3))).andExpect(status().isBadRequest());

            JsonNode favourites = body(call(get(API + "/me/places?kind=FAVOURITE"), Caller.CUSTOMER, ana));
            assertThat(favourites.get("data").get(0).get("place").get("name").asText()).isEqualTo("Lisbon");
            assertThat(body(call(get(API + "/me/places?kind=FAVOURITE"), Caller.CUSTOMER, subscriber()))
                    .get("data")).isEmpty();
            assertThat(body(call(get(API + "/me/place-marks?placeIds=" + lisbon + "," + porto), Caller.CUSTOMER,
                    ana))).hasSize(2);

            call(delete(PLACES + "/" + lisbon + "/favourite"), Caller.CUSTOMER, ana).andExpect(status().isNoContent());
            call(delete(PLACES + "/" + porto + "/visited"), Caller.CUSTOMER, ana).andExpect(status().isNoContent());
            assertThat(body(call(get(API + "/me/places?kind=VISITED"), Caller.CUSTOMER, ana)).get("data")).isEmpty();
            assertThat(body(call(get(API + "/me/place-marks?placeIds="), Caller.CUSTOMER, ana))).isEmpty();
        }

        @Test
        void a_mark_on_a_merged_place_goes_on_its_target() throws Exception {
            UUID duplicate = idOf(place("PT", "CITY", "Lisboa", null));
            call(post(PLACES + "/" + duplicate + "/merge"), Caller.ADMIN, UUID.randomUUID(), Map.of("targetId", lisbon));

            call(put(PLACES + "/" + duplicate + "/favourite"), Caller.CUSTOMER, ana)
                    .andExpect(jsonPath("$.placeId").value(lisbon.toString()));
        }
    }

    @Nested
    class Reviews {

        @Test
        void a_user_has_one_review_per_place_which_they_change_and_remove() throws Exception {
            JsonNode first = review(lisbon, ana, 4);
            JsonNode again = review(lisbon, ana, 5);

            assertThat(again.get("id").asText()).isEqualTo(first.get("id").asText());
            assertThat(again.get("title").asText()).isEqualTo("Lovely");
            assertThat(again.get("body").isNull()).isTrue();
            assertThat(again.get("languageCode").asText()).isEqualTo("pt");
            assertThat(first.get("createdAt").isNull()).isFalse();
            call(get(PLACES + "/" + lisbon + "/reviews/mine"), Caller.CUSTOMER, ana)
                    .andExpect(jsonPath("$.rating").value(5));

            call(delete(PLACES + "/" + lisbon + "/reviews/mine"), Caller.CUSTOMER, ana)
                    .andExpect(status().isNoContent());
            call(get(PLACES + "/" + lisbon + "/reviews/mine"), Caller.CUSTOMER, ana).andExpect(status().isNotFound());
            call(put(PLACES + "/" + lisbon + "/reviews/mine"), Caller.CUSTOMER, ana, Map.of("rating", 6))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void reviews_are_summarised_and_listed_by_date_or_rating() throws Exception {
            review(lisbon, ana, 5);
            review(lisbon, subscriber(), 3);
            review(lisbon, subscriber(), 4);

            JsonNode summary = body(call(get(PLACES + "/" + lisbon + "/reviews/summary"), Caller.CUSTOMER, ana));
            assertThat(summary.get("count").asLong()).isEqualTo(3);
            assertThat(summary.get("averageRating").asDouble()).isEqualTo(4.0);
            assertThat(body(call(get(PLACES + "/" + porto + "/reviews/summary"), Caller.CUSTOMER, ana))
                    .get("averageRating").isNull()).isTrue();

            for (Map.Entry<String, List<Integer>> sort : Map.of("HIGHEST", List.of(5, 4, 3), "LOWEST",
                    List.of(3, 4, 5), "NEWEST", List.of(4, 3, 5)).entrySet()) {
                List<Integer> ratings = new ArrayList<>();
                String cursor = null;
                do {
                    JsonNode page = body(call(get(PLACES + "/" + lisbon + "/reviews?limit=2&sort=" + sort.getKey()
                            + (cursor == null ? "" : "&cursor=" + cursor)), Caller.CUSTOMER, ana)
                            .andExpect(status().isOk()));
                    page.get("data").forEach(review -> ratings.add(review.get("rating").asInt()));
                    cursor = page.get("hasMore").asBoolean() ? page.get("nextCursor").asText() : null;
                    if (cursor != null && sort.getKey().equals("HIGHEST")) {
                        call(get(PLACES + "/" + lisbon + "/reviews?sort=LOWEST&cursor=" + cursor), Caller.CUSTOMER,
                                ana).andExpect(status().isBadRequest());
                    }
                } while (cursor != null);
                assertThat(ratings).as(sort.getKey()).isEqualTo(sort.getValue());
            }
        }

        @Test
        void three_open_reports_hide_a_review_until_staff_decide() throws Exception {
            String id = review(lisbon, ana, 1).get("id").asText();
            UUID first = subscriber();

            report(id, ana, 422);
            report(id, first, 201);
            report(id, first, 422);
            report(id, subscriber(), 201);
            assertThat(body(call(get(PLACES + "/" + lisbon + "/reviews"), Caller.CUSTOMER, ana)).get("data"))
                    .hasSize(1);
            report(id, subscriber(), 201);

            assertThat(body(call(get(PLACES + "/" + lisbon + "/reviews"), Caller.CUSTOMER, ana)).get("data"))
                    .isEmpty();
            call(get(PLACES + "/" + lisbon + "/reviews/mine"), Caller.CUSTOMER, ana)
                    .andExpect(jsonPath("$.isHidden").value(true));
            report(id, subscriber(), 404);

            JsonNode open = body(call(get(API + "/review-reports"), Caller.INSPECTOR, UUID.randomUUID()));
            assertThat(open.get("data")).hasSize(3);
            call(post(API + "/review-reports/" + open.get("data").get(0).get("id").asText() + "/resolve?isHiding=false"),
                    Caller.SUPPORT, UUID.randomUUID())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("KEPT"));
            assertThat(body(call(get(PLACES + "/" + lisbon + "/reviews"), Caller.CUSTOMER, ana)).get("data"))
                    .hasSize(1);
            assertThat(body(call(get(API + "/review-reports?isOpen=false"), Caller.SUPPORT, UUID.randomUUID()))
                    .get("data")).hasSize(3);
            call(post(API + "/review-reports/" + UUID.randomUUID() + "/resolve?isHiding=true"), Caller.SUPPORT,
                    UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        void staff_hide_or_show_any_review() throws Exception {
            String id = review(lisbon, ana, 2).get("id").asText();
            String reported = review(porto, ana, 2).get("id").asText();
            report(reported, subscriber(), 201);

            call(post(API + "/reviews/" + id + "/moderate?isHiding=true"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.isHidden").value(true))
                    .andExpect(jsonPath("$.hiddenReason").value("Hidden by staff"));
            call(post(API + "/reviews/" + id + "/moderate?isHiding=false&reason=x"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.isHidden").value(false));
            call(post(API + "/reviews/" + id + "/moderate?isHiding=true"), Caller.INSPECTOR, UUID.randomUUID())
                    .andExpect(status().isForbidden());
            call(post(API + "/reviews/" + UUID.randomUUID() + "/moderate?isHiding=true"), Caller.ADMIN,
                    UUID.randomUUID()).andExpect(status().isNotFound());

            String reportId = body(call(get(API + "/review-reports"), Caller.ADMIN, UUID.randomUUID())).get("data")
                    .get(0).get("id").asText();
            call(post(API + "/review-reports/" + reportId + "/resolve?isHiding=true"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(jsonPath("$.status").value("HIDDEN"));
            assertThat(body(call(get(PLACES + "/" + porto + "/reviews"), Caller.CUSTOMER, ana)).get("data")).isEmpty();
        }
    }

    @Nested
    class Questions {

        AiTextRequestedEvent lastQuestion() {
            List<AiTextRequestedEvent> asked = published.published().stream().map(p -> p.payload())
                    .filter(AiTextRequestedEvent.class::isInstance).map(AiTextRequestedEvent.class::cast).toList();
            return asked.get(asked.size() - 1);
        }

        JsonNode ask(String question) throws Exception {
            return body(call(post(PLACES + "/" + lisbon + "/questions"), Caller.CUSTOMER, ana,
                    Map.of("question", question)).andExpect(status().isAccepted()));
        }

        @Test
        void a_question_is_answered_privately_for_its_asker() throws Exception {
            JsonNode asked = ask("  Is the tram busy?  ");
            assertThat(asked.get("state").asText()).isEqualTo("GENERATING");
            assertThat(lastQuestion().userPrompt()).contains("Lisbon", "Question: Is the tram busy?");

            aiReplies.onTextCompleted(new AiTextCompletedEvent(lastQuestion().jobId(), "place-question",
                    AiJobStatus.COMPLETED, "{\"answer\": \"Very\", \"tips\": [], \"caveats\": []}", null, "gpt-test",
                    5, 5, 1, null, Instant.now()));

            String path = API + "/questions/" + asked.get("id").asText();
            call(get(path), Caller.CUSTOMER, ana)
                    .andExpect(jsonPath("$.state").value("READY"))
                    .andExpect(jsonPath("$.answer.answer").value("Very"));
            call(get(path), Caller.CUSTOMER, subscriber()).andExpect(status().isNotFound());
            call(get(path), Caller.SUPPORT, UUID.randomUUID()).andExpect(status().isOk());
            assertThat(body(call(get(PLACES + "/" + lisbon + "/questions/mine"), Caller.CUSTOMER, ana))
                    .get("data")).hasSize(1);
        }

        @Test
        void an_unreadable_failed_or_overdue_answer_fails_the_question() throws Exception {
            String unreadable = ask("One?").get("id").asText();
            aiReplies.onTextCompleted(new AiTextCompletedEvent(lastQuestion().jobId(), "place-question",
                    AiJobStatus.COMPLETED, "{\"tips\": []}", null, null, 0, 0, 1, null, Instant.now()));
            String failed = ask("Two?").get("id").asText();
            aiReplies.onTextCompleted(new AiTextCompletedEvent(lastQuestion().jobId(), "place-question",
                    AiJobStatus.FAILED, null, null, null, 0, 0, 3, "timeout", Instant.now()));
            String overdue = ask("Three?").get("id").asText();
            clock.advance(Duration.ofMinutes(31));
            contentJobs.run();

            for (String id : List.of(unreadable, failed, overdue)) {
                call(get(API + "/questions/" + id), Caller.CUSTOMER, ana).andExpect(jsonPath("$.state").value("FAILED"));
            }
            aiReplies.onTextCompleted(new AiTextCompletedEvent(UUID.randomUUID(), "place-question",
                    AiJobStatus.COMPLETED, "{}", null, null, 0, 0, 1, null, Instant.now()));
        }

        @Test
        void questions_have_a_length_limit_and_stop_at_the_ai_limits() throws Exception {
            call(post(PLACES + "/" + lisbon + "/questions"), Caller.CUSTOMER, ana, Map.of("question", "x".repeat(1001)))
                    .andExpect(status().isBadRequest());
            jdbc.update("insert into destination_ai_user_usage (user_id, day, generations) values (?, ?, 30)", ana,
                    LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
            call(post(PLACES + "/" + lisbon + "/questions"), Caller.CUSTOMER, ana, Map.of("question", "Hi?"))
                    .andExpect(status().isUnprocessableEntity());
            jdbc.update("insert into destination_ai_usage (day, input_tokens, output_tokens, jobs) "
                    + "values (?, 5000000, 0, 1)", LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
            call(post(PLACES + "/" + lisbon + "/questions"), Caller.CUSTOMER, subscriber(), Map.of("question", "Hi?"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    class Forgetting {

        @Test
        void a_deleted_users_marks_reviews_and_questions_are_forgotten() throws Exception {
            call(put(PLACES + "/" + lisbon + "/favourite"), Caller.CUSTOMER, ana);
            review(lisbon, ana, 5);
            String question = body(call(post(PLACES + "/" + lisbon + "/questions"), Caller.CUSTOMER, ana,
                    Map.of("question", "Hi?"))).get("id").asText();

            userEvents.onUserDeleted(new UserDeletedEvent(ana));

            assertThat(body(call(get(API + "/me/places?kind=FAVOURITE"), Caller.CUSTOMER, ana)).get("data")).isEmpty();
            assertThat(body(call(get(PLACES + "/" + lisbon + "/reviews/summary"), Caller.CUSTOMER, ana))
                    .get("count").asLong()).isZero();
            call(get(API + "/questions/" + question), Caller.SUPPORT, UUID.randomUUID())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Semantic {

        float[] vector(int hot) {
            float[] vector = new float[DIMENSIONS];
            vector[hot] = 1f;
            vector[DIMENSIONS - 1] = 0.1f;
            return vector;
        }

        AiEmbeddingRequestedEvent lastEmbedding() {
            List<AiEmbeddingRequestedEvent> all = published.published().stream().map(p -> p.payload())
                    .filter(AiEmbeddingRequestedEvent.class::isInstance).map(AiEmbeddingRequestedEvent.class::cast)
                    .toList();
            return all.get(all.size() - 1);
        }

        void embedPlaces() {
            contentJobs.run();
            AiEmbeddingRequestedEvent job = lastEmbedding();
            List<float[]> vectors = new ArrayList<>();
            for (String input : job.inputs()) {
                vectors.add(vector(input.startsWith("Lisbon") ? 0 : input.startsWith("Porto") ? 1 : 2));
            }
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(job.jobId(), "place-embedding",
                    AiJobStatus.COMPLETED, vectors, "embed-test", 10, null, Instant.now()));
        }

        List<String> placeIds(JsonNode found) {
            List<String> ids = new ArrayList<>();
            found.get("results").forEach(result -> ids.add(result.get("place").get("id").asText()));
            return ids;
        }

        JsonNode search(String query) throws Exception {
            return body(call(get(PLACES + "/semantic").param("q", query), Caller.CUSTOMER, ana)
                    .andExpect(status().isOk()));
        }

        @Test
        void places_are_embedded_in_the_background_and_unchanged_ones_are_not_sent_again() throws Exception {
            embedPlaces();
            assertThat(lastEmbedding().inputs()).anySatisfy(input -> assertThat(input)
                    .contains("Lisbon (city)", "Country: PT", "Tags: coast"));
            published.reset();

            jdbc.update("update destination_place_embeddings set checked_at = null");
            contentJobs.run();

            assertThat(published.published()).noneMatch(p -> p.payload() instanceof AiEmbeddingRequestedEvent);
        }

        @Test
        void a_place_is_embedded_with_its_other_names_categories_and_overview() throws Exception {
            UUID beaches = idOf(body(call(post("/api/v1/destinations/categories"), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("slug", "beaches", "name", "Beaches", "wikidataClassIds", List.of()))
                    .andExpect(status().isCreated())));
            UUID cascais = idOf(place("PT", "AREA", "Cascais", lisbon, "categoryIds", List.of(beaches),
                    "names", List.of(Map.of("languageCode", "pt", "name", "Cascais"),
                            Map.of("languageCode", "fr", "name", "Cascaes"))));
            jdbc.update("""
                    insert into destination_place_contents (id, created_at, updated_at, citations, content,
                        failed_attempts, language_code, section, status, place_id)
                    values (gen_random_uuid(), now(), now(), '[]'::jsonb, ?::jsonb, 0, 'en', 'OVERVIEW', 'READY', ?)""",
                    "{\"summary\": \"A fishing town by the sea.\", \"highlights\": [\"Boca do Inferno\"], "
                            + "\"goodFor\": [\"surfing\"]}", cascais);

            contentJobs.run();

            assertThat(lastEmbedding().inputs()).filteredOn(input -> input.startsWith("Cascais")).singleElement()
                    .isEqualTo("Cascais (area), in Lisbon. Country: PT. Also called: Cascaes. Categories: Beaches. "
                            + "A fishing town by the sea. Known for: Boca do Inferno; surfing.");
        }

        @Test
        void a_search_keeps_to_one_kind_of_place_or_to_what_lies_within_a_place() throws Exception {
            UUID belem = idOf(place("PT", "DISTRICT", "Belem", lisbon));
            embedPlaces();
            search("seaside capital");
            AiEmbeddingRequestedEvent query = lastEmbedding();
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(query.jobId(), "query-embedding",
                    AiJobStatus.COMPLETED, List.of(vector(0)), "embed-test", 2, null, Instant.now()));

            JsonNode districts = body(call(get(PLACES + "/semantic").param("q", "seaside capital")
                    .param("kind", "DISTRICT"), Caller.CUSTOMER, ana).andExpect(status().isOk()));
            JsonNode inLisbon = body(call(get(PLACES + "/semantic").param("q", "seaside capital")
                    .param("withinId", lisbon.toString()), Caller.CUSTOMER, ana).andExpect(status().isOk()));

            assertThat(placeIds(districts)).containsExactly(belem.toString());
            assertThat(placeIds(inLisbon)).contains(belem.toString()).doesNotContain(porto.toString());
        }

        @Test
        void a_query_is_embedded_once_and_then_answered_at_once() throws Exception {
            embedPlaces();

            assertThat(search("seaside capital").get("state").asText()).isEqualTo("GENERATING");
            assertThat(search("seaside capital").get("state").asText()).isEqualTo("GENERATING");
            AiEmbeddingRequestedEvent query = lastEmbedding();
            assertThat(query.task()).isEqualTo("query-embedding");
            assertThat(query.inputs()).containsExactly("seaside capital");
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(query.jobId(), "query-embedding",
                    AiJobStatus.COMPLETED, List.of(vector(0)), "embed-test", 2, null, Instant.now()));
            published.reset();

            JsonNode found = search("Seaside  Capital");
            assertThat(found.get("state").asText()).isEqualTo("READY");
            assertThat(found.get("results").get(0).get("place").get("name").asText()).isEqualTo("Lisbon");
            assertThat(published.published()).isEmpty();
            assertThat(body(call(get(PLACES + "/semantic").param("q", "seaside capital").param("countryCode", "es"), Caller.CUSTOMER, ana))
                    .get("results")).isEmpty();
        }

        @Test
        void similar_places_come_from_the_places_own_embedding() throws Exception {
            assertThat(body(call(get(PLACES + "/" + lisbon + "/similar"), Caller.CUSTOMER, ana)).get("state")
                    .asText()).isEqualTo("GENERATING");
            embedPlaces();

            JsonNode similar = body(call(get(PLACES + "/" + lisbon + "/similar?isSameCountry=true&limit=500"),
                    Caller.CUSTOMER, ana));
            assertThat(similar.get("state").asText()).isEqualTo("READY");
            assertThat(similar.get("results").findValuesAsText("name")).contains("Porto").doesNotContain("Lisbon");
        }

        @Test
        void a_failed_or_malformed_embedding_is_tried_again_later() throws Exception {
            contentJobs.run();
            AiEmbeddingRequestedEvent places = lastEmbedding();
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(places.jobId(), "place-embedding",
                    AiJobStatus.FAILED, null, null, 0, "down", Instant.now()));
            published.reset();
            contentJobs.run();
            AiEmbeddingRequestedEvent retried = lastEmbedding();
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(retried.jobId(), "place-embedding",
                    AiJobStatus.COMPLETED, List.of(new float[3]), "embed-test", 1, null, Instant.now()));
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(UUID.randomUUID(), "place-embedding",
                    AiJobStatus.COMPLETED, List.of(), "embed-test", 1, null, Instant.now()));

            search("old town");
            AiEmbeddingRequestedEvent query = lastEmbedding();
            aiReplies.onEmbeddingCompleted(new AiEmbeddingCompletedEvent(query.jobId(), "query-embedding",
                    AiJobStatus.FAILED, null, null, 0, "down", Instant.now()));
            assertThat(search("old town").get("state").asText()).isEqualTo("FAILED");
            clock.advance(Duration.ofMinutes(6));
            assertThat(search("old town").get("state").asText()).isEqualTo("GENERATING");
        }

        @Test
        void a_query_must_be_reasonable_and_waits_at_the_limits() throws Exception {
            call(get(PLACES + "/semantic").param("q", " "), Caller.CUSTOMER, ana).andExpect(status().isUnprocessableEntity());
            call(get(PLACES + "/semantic?q=" + "x".repeat(301)), Caller.CUSTOMER, ana)
                    .andExpect(status().isUnprocessableEntity());
            jdbc.update("insert into destination_ai_usage (day, input_tokens, output_tokens, jobs) "
                    + "values (?, 5000000, 0, 1)", LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));

            assertThat(search("castles").get("state").asText()).isEqualTo("UNAVAILABLE");
            assertThat(semantic.embedDue()).isZero();
            assertThat(body(call(get(PLACES + "/semantic?q=castles&withinId=" + lisbon), Caller.CUSTOMER, ana))
                    .get("state").asText()).isEqualTo("UNAVAILABLE");
        }

        @Test
        void a_job_left_hanging_is_released_for_the_next_run() throws Exception {
            contentJobs.run();
            clock.advance(Duration.ofMinutes(31));
            published.reset();

            contentJobs.run();

            assertThat(lastEmbedding().inputs()).hasSize(2);
        }
    }
}
