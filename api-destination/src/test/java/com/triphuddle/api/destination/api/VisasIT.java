package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.user.ResidencePermitKind;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent.ResidencePermit;
import com.triphuddle.api.destination.job.ContentJobs;
import com.triphuddle.api.destination.job.ImportJobRunner;
import com.triphuddle.api.destination.messaging.AiReplyListener;
import com.triphuddle.api.destination.messaging.UserEventListener;
import com.triphuddle.api.destination.visa.VisaRuleImportService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DST-7: visa rules from the passport index, each traveller's best passport, and shared AI guidance. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class VisasIT extends DestinationApiSupport {

    static final String RULES = """
            Passport,Destination,Requirement
            RO,TH,30
            DE,TH,visa free
            RO,US,eta
            DE,US,eta
            RO,RO,-1
            US,TH,e-visa
            XX,TH,visa free
            RO,JP,something new
            ROU,TH,visa free
            """;
    static final String GUIDANCE = "{\"summary\": \"Apply online\", \"steps\": [\"Fill the form\"]}";

    @Autowired AiReplyListener aiReplies;
    @Autowired UserEventListener userEvents;
    @Autowired VisaRuleImportService visaImports;
    @Autowired ImportJobRunner importJobRunner;
    @Autowired ContentJobs contentJobs;

    UUID bangkok;
    UUID newYork;

    @BeforeEach
    void rulesAndPlaces() throws Exception {
        country("TH");
        country("US");
        bangkok = idOf(place("TH", "CITY", "Bangkok", null));
        newYork = idOf(place("US", "CITY", "New York", null));
        given(visaDataset.download(anyString())).willReturn(RULES);
        call(post("/api/v1/destinations/visa-rules/imports"), Caller.ADMIN, UUID.randomUUID())
                .andExpect(status().isAccepted());
        importJobRunner.runWaiting();
    }

    void documents(UUID userId, List<String> nationalities, ResidencePermit... permits) {
        userEvents.onTravelDocumentsUpdated(new UserTravelDocumentsUpdatedEvent(userId, nationalities,
                List.of(permits), Instant.now()));
    }

    UUID planFor(UUID owner, UUID groupId, LocalDate end, UUID... places) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("title", "Far away", "startDate", end.minusDays(5),
                "endDate", end, "placeIds", List.of(places)));
        if (groupId != null) {
            request.put("groupId", groupId);
        }
        return idOf(body(call(post("/api/v1/destinations/trip-plans"), Caller.CUSTOMER, owner, request)
                .andExpect(status().isCreated())));
    }

    JsonNode visas(UUID plan, Caller caller, UUID viewer) throws Exception {
        return body(call(get("/api/v1/destinations/trip-plans/" + plan + "/visas"), caller, viewer)
                .andExpect(status().isOk()));
    }

    List<AiTextRequestedEvent> requested() {
        return published.published().stream().map(p -> p.payload())
                .filter(AiTextRequestedEvent.class::isInstance).map(AiTextRequestedEvent.class::cast).toList();
    }

    void answer(AiTextRequestedEvent job, AiJobStatus status, String output) {
        aiReplies.onTextCompleted(new AiTextCompletedEvent(job.jobId(), "visa-guidance", status, output, null,
                "gpt-test", 10, 10, 1, status == AiJobStatus.FAILED ? "timeout" : null, Instant.now()));
    }

    @Nested
    class Rules {

        @Test
        void the_import_loads_the_rules_it_understands_and_skips_the_rest() throws Exception {
            JsonNode imports = body(call(get("/api/v1/destinations/visa-rules/imports?page=1&pageSize=5"),
                    Caller.INSPECTOR, UUID.randomUUID()).andExpect(status().isOk())).get("data");

            assertThat(imports.get(0).get("status").asText()).isEqualTo("COMPLETED");
            assertThat(imports.get(0).get("ruleCount").asInt()).isEqualTo(7);
            assertThat(imports.get(0).get("skippedCount").asInt()).isEqualTo(2);
            call(post("/api/v1/destinations/visa-rules/imports"), Caller.INSPECTOR, UUID.randomUUID())
                    .andExpect(status().isForbidden());
        }

        @Test
        void one_import_at_a_time_and_a_bad_dataset_fails_it() throws Exception {
            given(visaDataset.download(anyString())).willReturn("not,a,dataset\n1,2,3");
            visaImports.queue(UUID.randomUUID());
            call(post("/api/v1/destinations/visa-rules/imports"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isUnprocessableEntity());

            assertThat(visaImports.runNext().status().name()).isEqualTo("FAILED");
            assertThat(visaImports.runNext()).isNull();
        }

        @Test
        void a_run_that_died_is_given_up_when_the_next_is_asked_for() {
            UUID stuck = visaImports.queue(UUID.randomUUID()).id();
            jdbc.update("update destination_visa_rule_imports set status = 'RUNNING', started_at = ? where id = ?",
                    java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(2))), stuck);

            visaImports.queue(UUID.randomUUID());

            assertThat(jdbc.queryForObject("select status from destination_visa_rule_imports where id = ?",
                    String.class, stuck)).isEqualTo("FAILED");
        }

        @Test
        void a_single_check_uses_the_rule_and_asks_for_guidance() throws Exception {
            UUID user = subscriber();

            JsonNode check = body(call(get("/api/v1/destinations/visas?passport=ro&destination=th"), Caller.CUSTOMER,
                    user).andExpect(status().isOk()));

            assertThat(check.get("requirement").asText()).isEqualTo("VISA_FREE");
            assertThat(check.get("allowedDays").asInt()).isEqualTo(30);
            assertThat(check.get("guidance").get("state").asText()).isEqualTo("GENERATING");
            call(get("/api/v1/destinations/visas?passport=ROU&destination=TH"), Caller.CUSTOMER, user)
                    .andExpect(status().isNotFound());
            call(get("/api/v1/destinations/visas?passport=RO&destination=RO"), Caller.CUSTOMER, user)
                    .andExpect(jsonPath("$.requirement").value("CITIZEN"))
                    .andExpect(jsonPath("$.guidance.state").value("NOT_NEEDED"));
        }
    }

    @Nested
    class Travellers {

        @Test
        void each_traveller_travels_on_their_easiest_passport() throws Exception {
            UUID ana = subscriber();
            documents(ana, List.of("RO", "DE"));
            UUID plan = planFor(ana, null, LocalDate.now().plusDays(30), bangkok, newYork);

            JsonNode response = visas(plan, Caller.CUSTOMER, ana);

            assertThat(response.get("destinationCodes").toString()).isEqualTo("[\"TH\",\"US\"]");
            assertThat(response.get("disclaimer").asText()).contains("not legal advice");
            JsonNode checks = response.get("travellers").get(0).get("checks");
            assertThat(checks.get(0).get("destinationCode").asText()).isEqualTo("TH");
            assertThat(checks.get(0).get("passportCode").asText()).isEqualTo("RO");
            assertThat(checks.get(0).get("byNationality")).hasSize(2);
            assertThat(checks.get(1).get("requirement").asText()).isEqualTo("ETA");
            assertThat(response.get("rulesUpdatedAt").isNull()).isFalse();
        }

        @Test
        void a_traveller_without_documents_is_told_to_add_them() throws Exception {
            UUID ana = subscriber();
            UUID plan = planFor(ana, null, LocalDate.now().plusDays(30), bangkok);

            JsonNode traveller = visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0);

            assertThat(traveller.get("isMissingDocuments").asBoolean()).isTrue();
            assertThat(traveller.get("checks").get(0).get("guidance").isNull()).isTrue();
            assertThat(requested()).isEmpty();
        }

        @Test
        void a_residence_permit_counts_only_if_it_lasts_the_whole_trip() throws Exception {
            UUID ana = subscriber();
            LocalDate end = LocalDate.now().plusDays(30);
            documents(ana, List.of("US"), new ResidencePermit("GB", ResidencePermitKind.PERMANENT, null),
                    new ResidencePermit("IE", ResidencePermitKind.TEMPORARY, end.minusDays(1)),
                    new ResidencePermit("FR", ResidencePermitKind.TEMPORARY, end));
            UUID plan = planFor(ana, null, end, bangkok);

            JsonNode check = visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0).get("checks").get(0);

            assertThat(check.get("residencePermitCodes").toString()).isEqualTo("[\"FR\",\"GB\"]");
        }

        @Test
        void group_admins_see_everyone_and_members_only_themselves() throws Exception {
            UUID groupId = UUID.randomUUID();
            license(LicenseTarget.GROUP, groupId, Map.of(ACCESS, "true"), Instant.now().plus(Duration.ofDays(30)));
            UUID admin = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            member(groupId, admin, "ADMIN");
            member(groupId, member, "MEMBER");
            documents(admin, List.of("DE"));
            documents(member, List.of("RO"));
            UUID plan = planFor(admin, groupId, LocalDate.now().plusDays(30), bangkok);

            JsonNode asAdmin = body(call(get("/api/v1/destinations/trip-plans/" + plan + "/visas?language=de"),
                    Caller.CUSTOMER, admin));
            JsonNode asMember = visas(plan, Caller.CUSTOMER, member);

            assertThat(asAdmin.get("isSeeingEveryone").asBoolean()).isTrue();
            assertThat(asAdmin.get("travellers")).hasSize(2);
            assertThat(asMember.get("isSeeingEveryone").asBoolean()).isFalse();
            assertThat(asMember.get("travellers").findValuesAsText("userId")).containsExactly(member.toString());
            assertThat(visas(plan, Caller.SUPPORT, UUID.randomUUID()).get("travellers")).hasSize(2);
        }

        @Test
        void stale_documents_are_ignored_and_a_deleted_users_are_forgotten() throws Exception {
            UUID ana = subscriber();
            Instant now = Instant.now();
            userEvents.onTravelDocumentsUpdated(new UserTravelDocumentsUpdatedEvent(ana, List.of("DE"), null, now));
            userEvents.onTravelDocumentsUpdated(new UserTravelDocumentsUpdatedEvent(ana, List.of("RO"), List.of(),
                    now.minusSeconds(60)));
            UUID plan = planFor(ana, null, LocalDate.now().plusDays(30), bangkok);
            assertThat(visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0).get("nationalities")
                    .toString()).isEqualTo("[\"DE\"]");

            userEvents.onUserDeleted(new UserDeletedEvent(ana));
            userEvents.onTravelDocumentsUpdated(new UserTravelDocumentsUpdatedEvent(ana, List.of("RO"), null,
                    Instant.now().plusSeconds(60)));

            assertThat(visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0).get("isMissingDocuments")
                    .asBoolean()).isTrue();
            userEvents.onUserDeleted(new UserDeletedEvent(UUID.randomUUID()));
        }
    }

    @Nested
    class Guidance {

        @Test
        void guidance_is_written_once_and_shared_by_everyone_in_the_same_case() throws Exception {
            UUID ana = subscriber();
            UUID bob = subscriber();
            documents(ana, List.of("US"));
            documents(bob, List.of("US"));
            UUID first = planFor(ana, null, LocalDate.now().plusDays(30), bangkok);
            UUID second = planFor(bob, null, LocalDate.now().plusDays(40), bangkok);

            visas(first, Caller.CUSTOMER, ana);
            visas(second, Caller.CUSTOMER, bob);
            assertThat(requested()).hasSize(1);
            assertThat(requested().get(0).isWebSearch()).isTrue();

            answer(requested().get(0), AiJobStatus.COMPLETED, GUIDANCE);
            JsonNode guidance = visas(second, Caller.CUSTOMER, bob).get("travellers").get(0).get("checks").get(0)
                    .get("guidance");
            assertThat(guidance.get("state").asText()).isEqualTo("READY");
            assertThat(guidance.get("content").get("summary").asText()).isEqualTo("Apply online");
        }

        @Test
        void a_failure_or_a_bad_answer_is_retried_only_after_a_while() throws Exception {
            UUID ana = subscriber();
            documents(ana, List.of("US"));
            UUID plan = planFor(ana, null, LocalDate.now().plusDays(30), bangkok);
            visas(plan, Caller.CUSTOMER, ana);

            answer(requested().get(0), AiJobStatus.FAILED, null);
            assertThat(visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0).get("checks").get(0)
                    .get("guidance").get("state").asText()).isEqualTo("FAILED");
            assertThat(requested()).hasSize(1);

            clock.advance(Duration.ofMinutes(61));
            visas(plan, Caller.CUSTOMER, ana);
            answer(requested().get(1), AiJobStatus.COMPLETED, "{\"summary\": 1}");
            clock.advance(Duration.ofMinutes(61));
            visas(plan, Caller.CUSTOMER, ana);
            assertThat(requested()).hasSize(3);
        }

        @Test
        void aged_guidance_is_served_while_refreshed_and_an_overdue_job_is_given_up() throws Exception {
            UUID ana = UUID.randomUUID();
            license(LicenseTarget.USER, ana, Map.of(ACCESS, "true"), Instant.now().plus(Duration.ofDays(365)));
            documents(ana, List.of("US"));
            UUID plan = planFor(ana, null, LocalDate.now().plusDays(90), bangkok);
            visas(plan, Caller.CUSTOMER, ana);
            answer(requested().get(0), AiJobStatus.COMPLETED, GUIDANCE);
            clock.advance(Duration.ofDays(31));

            JsonNode refreshing = visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0).get("checks").get(0)
                    .get("guidance");
            assertThat(refreshing.get("isRefreshing").asBoolean()).isTrue();
            assertThat(requested()).hasSize(2);

            clock.advance(Duration.ofMinutes(31));
            contentJobs.run();
            visas(plan, Caller.CUSTOMER, ana);
            assertThat(requested()).hasSize(3);
            aiReplies.onTextCompleted(new AiTextCompletedEvent(UUID.randomUUID(), "visa-guidance",
                    AiJobStatus.COMPLETED, GUIDANCE, null, null, 0, 0, 1, null, Instant.now()));
        }

        @Test
        void no_guidance_is_asked_for_past_the_hard_limit() throws Exception {
            UUID ana = subscriber();
            documents(ana, List.of("US"));
            UUID plan = planFor(ana, null, LocalDate.now().plusDays(30), bangkok);
            jdbc.update("insert into destination_ai_usage (day, input_tokens, output_tokens, jobs) values (?, 5000000, 0, 1)",
                    LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));

            assertThat(visas(plan, Caller.CUSTOMER, ana).get("travellers").get(0).get("checks").get(0)
                    .get("guidance").get("state").asText()).isEqualTo("UNAVAILABLE");
            assertThat(requested()).isEmpty();
        }
    }
}
