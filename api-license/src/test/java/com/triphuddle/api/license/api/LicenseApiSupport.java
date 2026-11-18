package com.triphuddle.api.license.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.api.license.messaging.GroupEventListener;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What api-license's integration tests share: MockMvc, the recorded events, and shortcuts to a published license,
 * a subscription and a group admin.
 */
@IntegrationTest
abstract class LicenseApiSupport {

    static final String LICENSES = "/api/v1/licenses";
    static final String SUBSCRIPTIONS = "/api/v1/licenses/subscriptions";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecordingMessageProducer published;

    @Autowired
    GroupEventListener groupEvents;

    final RequestPostProcessor staff = Caller.SUPPORT.request();

    @BeforeEach
    void forgetEarlierMessages() {
        published.reset();
    }

    UUID publishedLicense(LicenseTarget target, long priceMinor) throws Exception {
        UUID id = draft(target, priceMinor);
        mvc.perform(post(LICENSES + "/" + id + "/publish").with(staff)).andExpect(status().isOk());
        return id;
    }

    UUID draft(LicenseTarget target, long priceMinor) throws Exception {
        return idOf(body(send(post(LICENSES), LicenseFixtures.createLicense(target, priceMinor), staff)
                .andExpect(status().isCreated())));
    }

    ResultActions subscribe(UUID userId, UUID licenseId, UUID groupId, String code) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("licenseId", licenseId);
        request.put("groupId", groupId);
        request.put("code", code);
        return send(post(SUBSCRIPTIONS), request, Caller.CUSTOMER.request(userId));
    }

    UUID subscribed(UUID userId, UUID licenseId) throws Exception {
        return idOf(body(subscribe(userId, licenseId, null, null).andExpect(status().isCreated())));
    }

    UUID activated(UUID userId, UUID licenseId) throws Exception {
        UUID id = subscribed(userId, licenseId);
        mvc.perform(post(SUBSCRIPTIONS + "/" + id + "/activate").with(staff)).andExpect(status().isOk());
        return id;
    }

    /** As api-group would announce it: this user now administers this group. */
    void makeGroupAdmin(UUID groupId, UUID userId) {
        groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), groupId, userId, "ADMIN"));
    }

    String statusOf(UUID subscriptionId) {
        return jdbc.queryForObject("select status from license_subscriptions where id = ?", String.class,
                subscriptionId);
    }

    ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor caller)
            throws Exception {
        return mvc.perform(request.with(caller).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    static UUID idOf(JsonNode node) {
        return UUID.fromString(node.path("id").asText());
    }
}
