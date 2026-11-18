package com.triphuddle.api.group.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.security.GroupMatrices;
import com.triphuddle.testsupport.api.AbstractCrudApiIT;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupMembershipApiIT extends AbstractCrudApiIT<CreateGroupMembershipRequest, UpdateGroupMembershipRequest> {

    @Autowired
    JdbcTemplate jdbc;

    private UUID groupId;

    @BeforeEach
    void createAGroupTheActorAdministers() throws Exception {
        groupId = idOf(body(send(post("/api/v1/groups"), GroupFixtures.createGroup().build(), actor())
                .andExpect(status().isCreated())));
    }

    @Override
    protected String basePath() {
        return "/api/v1/groups/memberships";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return GroupMatrices.GROUP_PARTS;
    }

    @Override
    protected CreateGroupMembershipRequest validCreate() {
        return GroupFixtures.createMembership(groupId).build();
    }

    @Override
    protected UpdateGroupMembershipRequest validUpdate(UUID id) {
        return GroupFixtures.updateMembership(id, GroupRole.ADMIN);
    }

    @Override
    protected Map<String, CreateGroupMembershipRequest> invalidCreates() {
        return Map.of(
                "no group", GroupFixtures.createMembership(groupId).groupId(null).build(),
                "no user", GroupFixtures.createMembership(groupId).userId(null).build(),
                "no role", GroupFixtures.createMembership(groupId).role(null).build());
    }

    @Override
    protected Optional<CreateGroupMembershipRequest> clashingCreate(CreateGroupMembershipRequest existing) {
        return Optional.of(existing);
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateGroupMembershipRequest update) {
        assertThat(after.path("role").asText()).isEqualTo("ADMIN");
    }

    @Override
    protected int unknownIdStatus(Operation operation) {
        return operation == Operation.DELETE ? 403 : 404;
    }

    // --- GRP-2: one membership per user and group, enforced by the database ---------------------------------------

    @Test
    void theDatabaseRefusesASecondLiveMembershipButNotOneAfterADeletedOne() {
        UUID userId = UUID.randomUUID();
        String insert = """
                insert into group_memberships (id, group_id, user_id, role, created_at, updated_at, deleted_at)
                values (gen_random_uuid(), ?, ?, 'MEMBER', now(), now(), ?)""";
        jdbc.update(insert, groupId, userId, java.sql.Timestamp.from(java.time.Instant.now()));
        jdbc.update(insert, groupId, userId, null);

        assertThatThrownBy(() -> jdbc.update(insert, groupId, userId, null))
                .hasMessageContaining("uk_group_membership_group_user");
    }

    @Test
    void aMemberLeavesByDeletingTheirOwnMembership() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID membershipId = idOf(create(GroupFixtures.createMembership(groupId).userId(memberId).build()));

        mvc.perform(delete(basePath() + "/" + membershipId).with(Caller.CUSTOMER.request(memberId)))
                .andExpect(status().isOk());
    }

    @Test
    void membersSeeTheirGroupsMembersAndStrangersDoNot() throws Exception {
        UUID memberId = UUID.randomUUID();
        create(GroupFixtures.createMembership(groupId).userId(memberId).build());

        mvc.perform(get(basePath() + "/group/" + groupId).param("page", "1").param("pageSize", "10")
                .with(Caller.CUSTOMER.request(memberId))).andExpect(status().isOk());
        mvc.perform(get(basePath() + "/group/" + groupId).param("page", "1").param("pageSize", "10")
                .with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
    }
}
