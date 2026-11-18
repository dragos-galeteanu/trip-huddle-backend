package com.triphuddle.api.group.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.group.GroupCreatedContract;
import com.triphuddle.amqp.group.GroupCreatedEvent;
import com.triphuddle.amqp.group.GroupDeletedContract;
import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipCreatedContract;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.security.GroupMatrices;
import com.triphuddle.testsupport.api.AbstractCrudApiIT;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupApiIT extends AbstractCrudApiIT<CreateGroupRequest, UpdateGroupRequest> {

    @Override
    protected String basePath() {
        return "/api/v1/groups";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return GroupMatrices.GROUPS;
    }

    @Override
    protected CreateGroupRequest validCreate() {
        return GroupFixtures.createGroup().build();
    }

    @Override
    protected UpdateGroupRequest validUpdate(UUID id) {
        return GroupFixtures.updateGroup(id).build();
    }

    @Override
    protected Map<String, CreateGroupRequest> invalidCreates() {
        return Map.of(
                "blank name", GroupFixtures.createGroup().name(" ").build(),
                "name over 255 characters", GroupFixtures.createGroup().name("x".repeat(256)).build());
    }

    @Override
    protected Optional<CreateGroupRequest> clashingCreate(CreateGroupRequest existing) {
        return Optional.empty();
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateGroupRequest update) {
        assertThat(after.path("name").asText()).isEqualTo(update.getName());
        assertThat(after.path("description").asText()).isEqualTo(update.getDescription());
        assertThat(after.path("createdByUserId").asText()).isEqualTo(actorId.toString());
    }

    @Override
    protected int unknownIdStatus(Operation operation) {
        // Only the group's admins may delete it, so for a group that doesn't exist the policy says no first.
        return operation == Operation.DELETE ? 403 : 404;
    }

    // --- GRP-1: the creator is the first admin --------------------------------------------------------------------

    @Test
    void theCreatorBecomesTheGroupsFirstAdmin() throws Exception {
        UUID groupId = idOf(create(validCreate()));

        mvc.perform(get("/api/v1/groups/memberships/group/" + groupId).param("page", "1").param("pageSize", "10")
                        .with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(actorId.toString()))
                .andExpect(jsonPath("$.data[0].role").value("ADMIN"));
    }

    @Test
    void theOnlyAdminCannotLeave() throws Exception {
        UUID groupId = idOf(create(validCreate()));
        String membershipId = body(mvc.perform(get("/api/v1/groups/memberships/group/" + groupId)
                .param("page", "1").param("pageSize", "10").with(actor()))).at("/data/0/id").asText();

        mvc.perform(delete("/api/v1/groups/memberships/" + membershipId).with(actor()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least one Admin")));
    }

    // --- GRP-5: changes are published -----------------------------------------------------------------------------

    @Test
    void creatingPublishesTheGroupAndItsFirstMembershipDeletingPublishesToTheFanout() throws Exception {
        JsonNode group = create(validCreate());
        UUID groupId = idOf(group);
        mvc.perform(delete(basePath() + "/" + groupId).with(actor())).andExpect(status().isOk());

        published.assertPublished(new GroupCreatedEvent(groupId, group.path("name").asText()),
                GroupCreatedContract.GROUP_CREATED_QUEUE_NAME);
        assertThat(published.published()).filteredOn(p -> p.payload() instanceof GroupMembershipCreatedEvent)
                .singleElement()
                .satisfies(p -> assertThat(p.exchange())
                        .isEqualTo(GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_EXCHANGE_NAME));
        assertThat(published.published()).contains(new Published(
                new GroupDeletedEvent(groupId, group.path("name").asText()),
                GroupDeletedContract.GROUP_DELETED_EXCHANGE_NAME, ""));
    }

    // --- my groups ------------------------------------------------------------------------------------------------

    @Test
    void myGroupsAreTheOnesIBelongToAndNotDeleted() throws Exception {
        UUID kept = idOf(create(validCreate()));
        UUID deleted = idOf(create(validCreate()));
        mvc.perform(delete(basePath() + "/" + deleted).with(actor())).andExpect(status().isOk());

        JsonNode mine = body(mvc.perform(get(basePath() + "/mine").param("page", "1").param("pageSize", "50")
                .with(actor())).andExpect(status().isOk()));

        List<String> ids = mine.path("data").findValuesAsText("id");
        assertThat(ids).contains(kept.toString()).doesNotContain(deleted.toString());
    }

    @Test
    void aMemberReadsTheirGroupButAStrangerDoesNot() throws Exception {
        UUID groupId = idOf(create(validCreate()));

        mvc.perform(get(basePath() + "/" + groupId).with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        mvc.perform(get(basePath() + "/" + groupId).with(actor())).andExpect(status().isOk());
    }
}
