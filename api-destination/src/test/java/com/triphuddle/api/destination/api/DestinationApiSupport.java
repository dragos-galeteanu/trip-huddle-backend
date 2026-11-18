package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.platform.CountryCreatedEvent;
import com.triphuddle.api.destination.messaging.CountryEventListener;
import com.triphuddle.api.destination.messaging.GroupEventListener;
import com.triphuddle.api.destination.messaging.LicenseEventListener;
import com.triphuddle.api.destination.opendata.CommonsClient;
import com.triphuddle.api.destination.opendata.WikidataClient;
import com.triphuddle.api.destination.visa.VisaDatasetClient;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api-destination's integration tests share this: Postgres with PostGIS and pgvector in a container, Wikimedia and
 * the visa dataset replaced by mocks, a clock tests can move, and RabbitMQ replaced by a recorder. Countries,
 * licenses and groups are seeded the way api-platform, api-license and api-group announce them.
 */
@IntegrationTest
@Import(DestinationApiSupport.Outside.class)
abstract class DestinationApiSupport {

    static final String PLACES = "/api/v1/destinations/places";
    static final String ACCESS = "DESTINATIONS";

    @TestConfiguration(proxyBeanMethods = false)
    static class Outside {
        @Bean @Primary WikidataClient wikidataMock() { return Mockito.mock(WikidataClient.class); }
        @Bean @Primary CommonsClient commonsMock() { return Mockito.mock(CommonsClient.class); }
        @Bean @Primary VisaDatasetClient visaDatasetMock() { return Mockito.mock(VisaDatasetClient.class); }
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingMessageProducer published;
    @Autowired MutableClock clock;
    @Autowired CountryEventListener countryEvents;
    @Autowired LicenseEventListener licenseEvents;
    @Autowired GroupEventListener groupEvents;
    @Autowired WikidataClient wikidata;
    @Autowired CommonsClient commons;
    @Autowired VisaDatasetClient visaDataset;

    @BeforeEach
    void resetOutside() {
        published.reset();
        clock.reset();
        Mockito.reset(wikidata, commons, visaDataset);
    }

    @AfterEach
    void deleteEverything() {
        List<String> tables = jdbc.queryForList("select tablename from pg_tables where schemaname = 'public' "
                + "and tablename like 'destination\\_%'", String.class);
        jdbc.execute("truncate " + String.join(", ", tables) + " cascade");
    }

    /** A country as api-platform announced it. */
    void country(String alpha2) {
        countryEvents.onCountryCreated(new CountryCreatedEvent(UUID.randomUUID(), alpha2));
    }

    /** A user whose own license gives access to destinations, for a period ending in a month. */
    UUID subscriber() {
        UUID userId = UUID.randomUUID();
        license(LicenseTarget.USER, userId, Map.of(ACCESS, "true"), Instant.now().plus(Duration.ofDays(30)));
        return userId;
    }

    UUID license(LicenseTarget target, UUID subscriberId, Map<String, String> entitlements, Instant periodEnd) {
        UUID subscriptionId = UUID.randomUUID();
        licenseEvents.onSubscriptionActivated(new LicenseSubscriptionActivatedEvent(subscriptionId, UUID.randomUUID(),
                "EXPLORER", "Explorer", target, subscriberId, subscriberId, periodEnd, true, false, 999, "EUR",
                entitlements));
        return subscriptionId;
    }

    void member(UUID groupId, UUID userId, String role) {
        groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), groupId, userId, role));
    }

    /** A place created by an admin; the extra fields are added to the request. */
    JsonNode place(String countryCode, String kind, String name, UUID parentId, Object... extra) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("countryCode", countryCode);
        request.put("kind", kind);
        request.put("name", name);
        if (parentId != null) {
            request.put("parentId", parentId);
        }
        for (int i = 0; i < extra.length; i += 2) {
            request.put((String) extra[i], extra[i + 1]);
        }
        return body(call(post(PLACES), Caller.ADMIN, UUID.randomUUID(), request).andExpect(status().isCreated()));
    }

    ResultActions call(MockHttpServletRequestBuilder request, Caller caller, UUID userId, Object body)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request.with(caller.request(userId)));
    }

    ResultActions call(MockHttpServletRequestBuilder request, Caller caller, UUID userId) throws Exception {
        return call(request, caller, userId, null);
    }

    JsonNode body(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    static UUID idOf(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    /** The names of the items of a list, not of what they nest. */
    static List<String> names(JsonNode items) {
        List<String> names = new ArrayList<>();
        items.forEach(item -> names.add(item.get("name").asText()));
        return names;
    }

    static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
