package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.amqp.destination.ItineraryFinishedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.notification.SendPushRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
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

/** DST-6: trip plans and their AI itineraries, the monthly quota, and who may use them. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TripsIT extends DestinationApiSupport {

    static final String PLANS = "/api/v1/destinations/trip-plans";
    static final String ITINERARIES = "/api/v1/destinations/itineraries";
    static final String QUOTA = "AI_ITINERARIES_PER_MONTH";

    @Autowired AiReplyListener aiReplies;
    @Autowired ContentJobs contentJobs;

    UUID lisbon;
    UUID museum;
    UUID hidden;
    UUID traveller;

    @BeforeEach
    void lisbon() throws Exception {
        country("PT");
        lisbon = idOf(place("PT", "CITY", "Lisbon", null));
        museum = idOf(place("PT", "POI", "Tile Museum", lisbon));
        hidden = idOf(place("PT", "POI", "Closed Fort", lisbon));
        call(post(PLACES + "/" + hidden + "/hide"), Caller.ADMIN, UUID.randomUUID());
        traveller = planner(Map.of(ACCESS, "true", QUOTA, "2"));
    }

    UUID planner(Map<String, String> entitlements) {
        UUID userId = UUID.randomUUID();
        license(LicenseTarget.USER, userId, entitlements, Instant.now().plus(Duration.ofDays(30)));
        return userId;
    }

    Map<String, Object> planRequest(int days) {
        Map<String, Object> request = new HashMap<>();
        LocalDate start = LocalDate.now().plusDays(10);
        request.put("title", "  Lisbon long weekend  ");
        request.put("startDate", start);
        request.put("endDate", start.plusDays(days - 1L));
        request.put("placeIds", List.of(lisbon));
        request.put("mustSeePlaceIds", List.of(museum));
        request.put("interests", List.of("museums", "food"));
        request.put("notes", " Vegetarian ");
        return request;
    }

    UUID plan(UUID user, Map<String, Object> request) throws Exception {
        return idOf(body(call(post(PLANS), Caller.CUSTOMER, user, request).andExpect(status().isCreated())));
    }

    JsonNode generate(UUID plan, UUID user) throws Exception {
        return body(call(post(PLANS + "/" + plan + "/itineraries"), Caller.CUSTOMER, user)
                .andExpect(status().isAccepted()));
    }

    AiTextRequestedEvent lastRequested() {
        List<AiTextRequestedEvent> all = published.published().stream().map(p -> p.payload())
                .filter(AiTextRequestedEvent.class::isInstance).map(AiTextRequestedEvent.class::cast).toList();
        return all.get(all.size() - 1);
    }

    void answer(AiTextRequestedEvent job, String output) {
        aiReplies.onTextCompleted(new AiTextCompletedEvent(job.jobId(), job.task(), AiJobStatus.COMPLETED, output,
                List.of(), "gpt-test", 1000, 2000, 1, null, Instant.now()));
    }

    String itineraryAnswer(int days) {
        List<String> dayJson = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            dayJson.add("""
                    {"dayNumber": %d, "title": "Day %d in Lisbon", "summary": "Hills",
                     "items": [
                       {"timeOfDay": "MORNING", "title": "Tiles", "description": "Azulejos", "placeId": "%s",
                        "durationMinutes": 120, "tips": "Go early"},
                       {"timeOfDay": "AFTERNOON", "title": "Fort", "placeId": "%s", "durationMinutes": 0},
                       {"timeOfDay": "night", "title": "Fado", "placeId": "not-a-uuid"},
                       {"timeOfDay": "EVENING", "title": " "}
                     ]}""".formatted(day, day, museum, hidden));
        }
        return "{\"summary\": \"Three easy days\", \"unknown\": true, \"days\": [" + String.join(",", dayJson) + "]}";
    }

    JsonNode itinerary(UUID id, UUID user) throws Exception {
        return body(call(get(ITINERARIES + "/" + id), Caller.CUSTOMER, user).andExpect(status().isOk()));
    }

    int used(UUID ownerId) {
        return jdbc.queryForObject("select coalesce(sum(used), 0) from destination_ai_quota_usage where owner_id = ?",
                Integer.class, ownerId);
    }

    @Nested
    class Plans {

        @Test
        void a_plan_runs_up_to_30_days_and_10_known_listed_places() throws Exception {
            JsonNode plan = body(call(post(PLANS), Caller.CUSTOMER, traveller, planRequest(3))
                    .andExpect(status().isCreated()));
            assertThat(plan.get("title").asText()).isEqualTo("Lisbon long weekend");
            assertThat(plan.get("dayCount").asInt()).isEqualTo(3);
            assertThat(plan.get("createdAt").isNull()).isFalse();

            Map<String, Object> tooLong = planRequest(31);
            Map<String, Object> backwards = planRequest(3);
            backwards.put("endDate", LocalDate.now());
            Map<String, Object> tooMany = planRequest(3);
            List<UUID> eleven = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                eleven.add(idOf(place("PT", "CITY", "Town " + i, null)));
            }
            tooMany.put("placeIds", eleven);
            Map<String, Object> unlisted = planRequest(3);
            unlisted.put("placeIds", List.of(hidden));
            Map<String, Object> unknown = planRequest(3);
            unknown.put("mustSeePlaceIds", List.of(UUID.randomUUID()));
            for (Map<String, Object> request : List.of(tooLong, backwards, tooMany, unlisted, unknown)) {
                call(post(PLANS), Caller.CUSTOMER, traveller, request).andExpect(status().isUnprocessableEntity());
            }
            call(post(PLANS), Caller.CUSTOMER, UUID.randomUUID(), planRequest(3)).andExpect(status().isForbidden());
        }

        @Test
        void the_owner_lists_edits_and_deletes_their_plans_and_nobody_else_sees_them() throws Exception {
            UUID plan = plan(traveller, planRequest(3));
            Map<String, Object> edited = planRequest(2);
            edited.put("title", "Shorter");
            edited.put("notes", " ");
            edited.put("language", "PT");

            call(put(PLANS + "/" + plan), Caller.CUSTOMER, traveller, edited)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dayCount").value(2))
                    .andExpect(jsonPath("$.languageCode").value("pt"));
            assertThat(body(call(get(PLANS + "/mine?page=1&pageSize=10"), Caller.CUSTOMER, traveller)).get("data"))
                    .hasSize(1);
            call(get(PLANS + "/" + plan), Caller.CUSTOMER, subscriber()).andExpect(status().isForbidden());
            call(get(PLANS + "/" + plan), Caller.INSPECTOR, UUID.randomUUID()).andExpect(status().isOk());
            call(delete(PLANS + "/" + plan), Caller.CUSTOMER, subscriber()).andExpect(status().isForbidden());
            call(delete(PLANS + "/" + plan), Caller.CUSTOMER, traveller).andExpect(status().isNoContent());
            call(get(PLANS + "/" + plan), Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        void a_groups_plan_is_shared_by_its_members_and_deleted_by_its_creator_or_an_admin() throws Exception {
            UUID groupId = UUID.randomUUID();
            license(LicenseTarget.GROUP, groupId, Map.of(ACCESS, "true", QUOTA, "1"),
                    Instant.now().plus(Duration.ofDays(30)));
            UUID creator = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            UUID admin = UUID.randomUUID();
            member(groupId, creator, "MEMBER");
            member(groupId, member, "MEMBER");
            member(groupId, admin, "ADMIN");
            Map<String, Object> request = planRequest(2);
            request.put("groupId", groupId);
            UUID plan = plan(creator, request);
            UUID other = plan(creator, request);

            call(get(PLANS + "/" + plan), Caller.CUSTOMER, member).andExpect(status().isOk());
            assertThat(body(call(get(PLANS + "/groups/" + groupId + "?page=1&pageSize=10"), Caller.CUSTOMER, member))
                    .get("data")).hasSize(2);
            call(get(PLANS + "/groups/" + groupId + "?page=1&pageSize=10"), Caller.CUSTOMER, traveller)
                    .andExpect(status().isForbidden());
            call(delete(PLANS + "/" + plan), Caller.CUSTOMER, member).andExpect(status().isForbidden());
            call(delete(PLANS + "/" + plan), Caller.CUSTOMER, admin).andExpect(status().isNoContent());
            call(delete(PLANS + "/" + other), Caller.CUSTOMER, creator).andExpect(status().isNoContent());
        }
    }

    @Nested
    class Itineraries {

        @Test
        void an_itinerary_links_only_known_listed_places_and_tells_the_travellers() throws Exception {
            UUID plan = plan(traveller, planRequest(3));

            JsonNode requested = generate(plan, traveller);
            assertThat(requested.get("status").asText()).isEqualTo("GENERATING");
            assertThat(lastRequested().userPrompt()).contains("Tile Museum").doesNotContain("Closed Fort");
            assertThat(used(traveller)).isEqualTo(1);

            answer(lastRequested(), itineraryAnswer(4));

            JsonNode ready = itinerary(idOf(requested), traveller);
            assertThat(ready.get("status").asText()).isEqualTo("READY");
            assertThat(ready.get("summary").asText()).isEqualTo("Three easy days");
            assertThat(ready.get("days")).hasSize(3);
            JsonNode items = ready.get("days").get(0).get("items");
            assertThat(items).hasSize(3);
            assertThat(items.get(0).get("place").get("id").asText()).isEqualTo(museum.toString());
            assertThat(items.get(1).get("place").isNull()).isTrue();
            assertThat(items.get(1).get("timeOfDay").asText()).isEqualTo("AFTERNOON");
            assertThat(items.get(1).get("durationMinutes").isNull()).isTrue();
            ItineraryFinishedEvent finished = published.published().stream().map(p -> p.payload())
                    .filter(ItineraryFinishedEvent.class::isInstance).map(ItineraryFinishedEvent.class::cast)
                    .findFirst().orElseThrow();
            assertThat(finished.status()).isEqualTo("READY");
            assertThat(finished.travellerUserIds()).containsExactly(traveller);
            assertThat(finished.planUrl()).contains(plan.toString());
            published.assertPublishedCount(SendPushRequest.class, 1);
            assertThat(body(call(get(PLANS + "/" + plan + "/itineraries"), Caller.CUSTOMER, traveller))).hasSize(1);
        }

        @Test
        void a_failed_itinerary_gives_its_unit_of_quota_back() throws Exception {
            UUID plan = plan(traveller, planRequest(2));
            UUID first = idOf(generate(plan, traveller));
            call(post(PLANS + "/" + plan + "/itineraries"), Caller.CUSTOMER, traveller)
                    .andExpect(status().isUnprocessableEntity());

            answer(lastRequested(), "{\"days\": []}");

            assertThat(itinerary(first, traveller).get("status").asText()).isEqualTo("FAILED");
            assertThat(used(traveller)).isZero();
            SendPushRequest push = (SendPushRequest) published.published().stream().map(p -> p.payload())
                    .filter(SendPushRequest.class::isInstance).findFirst().orElseThrow();
            assertThat(push.title()).contains("couldn't");

            UUID second = idOf(generate(plan, traveller));
            aiReplies.onTextCompleted(new AiTextCompletedEvent(lastRequested().jobId(), "itinerary",
                    AiJobStatus.FAILED, null, null, null, 0, 0, 3, "model refused", Instant.now()));
            assertThat(itinerary(second, traveller).get("error").asText()).isEqualTo("model refused");
        }

        @Test
        void the_monthly_quota_is_the_licenses_and_a_license_without_it_writes_none() throws Exception {
            UUID plan = plan(traveller, planRequest(2));
            for (int i = 0; i < 2; i++) {
                generate(plan, traveller);
                answer(lastRequested(), itineraryAnswer(2));
            }
            call(post(PLANS + "/" + plan + "/itineraries"), Caller.CUSTOMER, traveller)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("used up")));

            UUID without = subscriber();
            call(post(PLANS + "/" + plan(without, planRequest(2)) + "/itineraries"), Caller.CUSTOMER, without)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("doesn't include")));

            UUID unlimited = planner(Map.of(ACCESS, "true", QUOTA, "Unlimited"));
            UUID unlimitedPlan = plan(unlimited, planRequest(2));
            for (int i = 0; i < 3; i++) {
                generate(unlimitedPlan, unlimited);
                answer(lastRequested(), itineraryAnswer(2));
            }
        }

        @Test
        void ai_planning_pauses_at_the_hard_token_limit_and_at_the_users_daily_share() throws Exception {
            UUID plan = plan(traveller, planRequest(2));
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            jdbc.update("insert into destination_ai_user_usage (user_id, day, generations) values (?, ?, 30)",
                    traveller, today);
            call(post(PLANS + "/" + plan + "/itineraries"), Caller.CUSTOMER, traveller)
                    .andExpect(status().isUnprocessableEntity());

            jdbc.update("insert into destination_ai_usage (day, input_tokens, output_tokens, jobs) "
                    + "values (?, 5000000, 0, 1)", today);
            UUID other = planner(Map.of(ACCESS, "true", QUOTA, "5"));
            call(post(PLANS + "/" + plan(other, planRequest(2)) + "/itineraries"), Caller.CUSTOMER, other)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("paused")));
        }

        @Test
        void an_overdue_itinerary_fails_and_a_late_answer_is_ignored() throws Exception {
            UUID plan = plan(traveller, planRequest(2));
            UUID id = idOf(generate(plan, traveller));
            AiTextRequestedEvent job = lastRequested();
            clock.advance(Duration.ofMinutes(31));

            contentJobs.run();

            assertThat(itinerary(id, traveller).get("error").asText()).contains("too long");
            answer(job, itineraryAnswer(2));
            assertThat(itinerary(id, traveller).get("status").asText()).isEqualTo("FAILED");
            assertThat(used(traveller)).isZero();
        }
    }

    @Nested
    class Editing {

        UUID ready(UUID plan) throws Exception {
            UUID id = idOf(generate(plan, traveller));
            answer(lastRequested(), itineraryAnswer(2));
            return id;
        }

        @Test
        void travellers_reorder_add_and_remove_items_of_a_ready_itinerary() throws Exception {
            UUID id = ready(plan(traveller, planRequest(2)));
            JsonNode items = itinerary(id, traveller).get("days").get(0).get("items");
            List<String> reversed = new ArrayList<>(List.of(items.get(2).get("id").asText(),
                    items.get(1).get("id").asText(), items.get(0).get("id").asText()));

            call(put(ITINERARIES + "/" + id + "/days/1/order"), Caller.CUSTOMER, traveller, reversed)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days[0].items[0].id").value(reversed.get(0)));
            call(put(ITINERARIES + "/" + id + "/days/1/order"), Caller.CUSTOMER, traveller, reversed.subList(0, 2))
                    .andExpect(status().isUnprocessableEntity());

            call(post(ITINERARIES + "/" + id + "/days/2/items"), Caller.CUSTOMER, traveller, Map.of(
                    "timeOfDay", "EVENING", "title", " Dinner ", "placeId", museum, "durationMinutes", 90,
                    "description", " ", "tips", "Book"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days[1].items[3].title").value("Dinner"));
            call(post(ITINERARIES + "/" + id + "/days/2/items"), Caller.CUSTOMER, traveller, Map.of(
                    "timeOfDay", "EVENING", "title", "Fort", "placeId", hidden))
                    .andExpect(status().isUnprocessableEntity());
            call(post(ITINERARIES + "/" + id + "/days/9/items"), Caller.CUSTOMER, traveller, Map.of(
                    "timeOfDay", "EVENING", "title", "Nowhere")).andExpect(status().isNotFound());

            call(delete(ITINERARIES + "/" + id + "/items/" + reversed.get(0)), Caller.CUSTOMER, traveller)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days[0].items.length()").value(2))
                    .andExpect(jsonPath("$.days[0].items[0].position").value(0));
            call(delete(ITINERARIES + "/" + id + "/items/" + UUID.randomUUID()), Caller.CUSTOMER, traveller)
                    .andExpect(status().isNotFound());
            call(delete(ITINERARIES + "/" + id + "/items/" + reversed.get(1)), Caller.CUSTOMER, subscriber())
                    .andExpect(status().isForbidden());
        }

        @Test
        void a_day_is_rewritten_on_request_and_kept_when_the_rewrite_fails() throws Exception {
            UUID id = ready(plan(traveller, planRequest(2)));

            call(post(ITINERARIES + "/" + id + "/days/1/regenerate?instructions=Less walking"), Caller.CUSTOMER,
                    traveller).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.days[0].isRegenerating").value(true));
            AiTextRequestedEvent job = lastRequested();
            assertThat(job.task()).isEqualTo("itinerary-day");
            assertThat(job.userPrompt()).contains("Less walking");
            call(post(ITINERARIES + "/" + id + "/days/1/regenerate"), Caller.CUSTOMER, traveller)
                    .andExpect(status().isUnprocessableEntity());

            answer(job, """
                    {"dayNumber": 1, "title": "", "items": [{"timeOfDay": "MORNING", "title": "Tram 28"}]}""");
            JsonNode day = itinerary(id, traveller).get("days").get(0);
            assertThat(day.get("title").asText()).isEqualTo("Day 1");
            assertThat(day.get("items").findValuesAsText("title")).containsExactly("Tram 28");

            call(post(ITINERARIES + "/" + id + "/days/1/regenerate"), Caller.CUSTOMER, traveller);
            answer(lastRequested(), "not json");
            assertThat(itinerary(id, traveller).get("days").get(0).get("items").findValuesAsText("title"))
                    .containsExactly("Tram 28");

            call(post(ITINERARIES + "/" + id + "/days/2/regenerate"), Caller.CUSTOMER, traveller);
            clock.advance(Duration.ofMinutes(31));
            contentJobs.run();
            assertThat(itinerary(id, traveller).get("days").get(1).get("isRegenerating").asBoolean()).isFalse();
        }

        @Test
        void an_itinerary_still_being_written_cannot_be_edited() throws Exception {
            UUID id = idOf(generate(plan(traveller, planRequest(2)), traveller));

            call(post(ITINERARIES + "/" + id + "/days/1/items"), Caller.CUSTOMER, traveller, Map.of(
                    "timeOfDay", "EVENING", "title", "Early")).andExpect(status().isUnprocessableEntity());
            call(get(ITINERARIES + "/" + UUID.randomUUID()), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isNotFound());
        }
    }
}
