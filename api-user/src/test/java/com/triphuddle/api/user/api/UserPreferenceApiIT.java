package com.triphuddle.api.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.api.user.entity.UserPreference;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.testsupport.security.Caller;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preferences live in MongoDB with ObjectId keys, so they are tested here rather than through the CRUD base.
 */
@UserIntegrationTest
class UserPreferenceApiIT {

    private static final String PREFERENCES = "/api/v1/users/user-preferences";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MongoTemplate mongo;

    @Test
    void theOwnerKeepsTheirPreferencesThroughTheirWholeLife() throws Exception {
        UUID owner = UserTestData.insertUser(jdbc, true);
        RequestPostProcessor me = Caller.CUSTOMER.request(owner);

        JsonNode created = body(send(post(PREFERENCES), UserFixtures.createPreference(owner).build(), me)
                .andExpect(status().isCreated()));
        String id = created.path("id").asText();
        assertThat(ObjectId.isValid(id)).isTrue();
        assertThat(created.path("isMarketingOptIn").asBoolean()).isFalse();

        mvc.perform(get(PREFERENCES + "/" + id).with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.toString()));
        send(put(PREFERENCES), UserFixtures.updatePreference(id, owner).build(), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.languageCode").value("fr"))
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(delete(PREFERENCES + "/" + id).with(me)).andExpect(status().isOk());
        mvc.perform(get(PREFERENCES + "/" + id).with(me)).andExpect(status().isNotFound());
    }

    @Test
    void othersSeeNothingStaffReadAndListAndOnlyAdminsDelete() throws Exception {
        UUID owner = UserTestData.insertUser(jdbc, true);
        String id = body(send(post(PREFERENCES), UserFixtures.createPreference(owner).build(),
                Caller.CUSTOMER.request(owner)).andExpect(status().isCreated())).path("id").asText();

        mvc.perform(get(PREFERENCES + "/" + id).with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        mvc.perform(get(PREFERENCES + "/" + id).with(Caller.INSPECTOR.request())).andExpect(status().isOk());
        mvc.perform(get(PREFERENCES).param("page", "1").param("pageSize", "5").with(Caller.INSPECTOR.request()))
                .andExpect(status().isOk());
        mvc.perform(get(PREFERENCES).param("page", "1").param("pageSize", "5").with(Caller.CUSTOMER.request()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PREFERENCES + "/" + id).with(Caller.SUPPORT.request())).andExpect(status().isForbidden());
        mvc.perform(delete(PREFERENCES + "/" + id + "/disable").with(Caller.SUPPORT.request()))
                .andExpect(status().isOk());
        mvc.perform(delete(PREFERENCES + "/" + id).with(Caller.ADMIN.request())).andExpect(status().isOk());
    }

    @Test
    void aUserHasAtMostOneSetOfPreferences() throws Exception {
        UUID owner = UserTestData.insertUser(jdbc, true);
        send(post(PREFERENCES), UserFixtures.createPreference(owner).build(), Caller.CUSTOMER.request(owner))
                .andExpect(status().isCreated());

        send(post(PREFERENCES), UserFixtures.createPreference(owner).build(), Caller.CUSTOMER.request(owner))
                .andExpect(status().isUnprocessableEntity());
        assertThatThrownBy(() -> mongo.insert(UserFixtures.preference(owner)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void refusesInvalidCodesAndAnUnknownUser() throws Exception {
        UUID owner = UserTestData.insertUser(jdbc, true);
        RequestPostProcessor me = Caller.CUSTOMER.request(owner);

        send(post(PREFERENCES), UserFixtures.createPreference(owner).languageCode("eng").build(), me)
                .andExpect(status().isBadRequest());
        send(post(PREFERENCES), UserFixtures.createPreference(owner).countryCode("R1").build(), me)
                .andExpect(status().isBadRequest());
        UUID ghost = UUID.randomUUID();
        send(post(PREFERENCES), UserFixtures.createPreference(ghost).build(), Caller.CUSTOMER.request(ghost))
                .andExpect(status().isNotFound());
        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("userId").is(ghost)), UserPreference.class))
                .isZero();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor caller)
            throws Exception {
        return mvc.perform(request.with(caller).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
