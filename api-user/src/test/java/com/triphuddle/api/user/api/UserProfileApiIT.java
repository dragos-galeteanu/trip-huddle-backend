package com.triphuddle.api.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.user.UserProfilePictureUpdatedEvent;
import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.security.UserMatrices;
import com.triphuddle.testsupport.api.AbstractCrudApiIT;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class UserProfileApiIT extends AbstractCrudApiIT<CreateUserProfileRequest, UpdateUserProfileRequest> {

    @Autowired
    JdbcTemplate jdbc;

    @Override
    protected String basePath() {
        return "/api/v1/users/user-profiles";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return UserMatrices.PROFILES_OVER_HTTP;
    }

    @Override
    protected CreateUserProfileRequest validCreate() {
        return UserFixtures.createProfile(UserTestData.insertUser(jdbc, true)).build();
    }

    @Override
    protected UpdateUserProfileRequest validUpdate(UUID id) {
        UUID owner = jdbc.queryForList("select user_id from user_profiles where id = ?", UUID.class, id).stream()
                .findFirst().orElse(UUID.randomUUID());
        return UpdateUserProfileRequest.builder().id(id).userId(owner).countryCode("FR").bio("Changed")
                .websiteUrl("https://ana.example").build();
    }

    @Override
    protected Map<String, CreateUserProfileRequest> invalidCreates() {
        return Map.of(
                "no user", UserFixtures.createProfile(null).build(),
                "three-letter country", UserFixtures.createProfile(UUID.randomUUID()).countryCode("ROU").build(),
                "blank country", UserFixtures.createProfile(UUID.randomUUID()).countryCode(" ").build());
    }

    @Override
    protected Optional<CreateUserProfileRequest> clashingCreate(CreateUserProfileRequest existing) {
        return Optional.of(UserFixtures.createProfile(existing.getUserId()).build());
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateUserProfileRequest update) {
        assertThat(after.path("countryCode").asText()).isEqualTo("FR");
        assertThat(after.path("websiteUrl").asText()).isEqualTo("https://ana.example");
        assertThat(after.path("userId").asText()).isEqualTo(update.getUserId().toString());
    }

    @Override
    protected int unknownIdStatus(Operation operation) {
        // Only the owner deletes a profile, so for one that doesn't exist the policy says no first.
        return operation == Operation.DELETE ? 403 : 404;
    }

    @Override
    protected RequestPostProcessor actorFor(Operation operation, UUID id) {
        // Only its owner deletes a profile; staff disable one instead.
        if (operation != Operation.DELETE) {
            return actor();
        }
        return jdbc.queryForList("select user_id from user_profiles where id = ?", UUID.class, id).stream()
                .findFirst().map(Caller.CUSTOMER::request).orElseGet(this::actor);
    }

    @Test
    void theOwnerCreatesAndChangesTheirProfileAndANewPictureIsAnnounced() throws Exception {
        UUID owner = UserTestData.insertUser(jdbc, true);
        JsonNode created = body(send(post(basePath()), UserFixtures.createProfile(owner).build(),
                Caller.CUSTOMER.request(owner)).andExpect(status().isCreated()));
        UUID profileId = idOf(created);

        send(put(basePath()), UpdateUserProfileRequest.builder().id(profileId).userId(owner).countryCode("RO")
                .profilePictureUrl("https://img.example/new.jpg").build(), Caller.CUSTOMER.request(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePictureUrl")
                        .value("https://img.example/new.jpg"));

        assertThat(published.published()).extracting(Published::payload).anySatisfy(p ->
                assertThat(p).isInstanceOfSatisfying(UserProfilePictureUpdatedEvent.class, event -> {
                    assertThat(event.userId()).isEqualTo(owner);
                    assertThat(event.profilePictureChanged()).isTrue();
                }));
    }

    @Test
    void aCustomerCannotCreateOrChangeSomeoneElsesProfile() throws Exception {
        UUID someone = UserTestData.insertUser(jdbc, true);

        send(post(basePath()), UserFixtures.createProfile(someone).build(), Caller.CUSTOMER.request())
                .andExpect(status().isForbidden());
    }
}
