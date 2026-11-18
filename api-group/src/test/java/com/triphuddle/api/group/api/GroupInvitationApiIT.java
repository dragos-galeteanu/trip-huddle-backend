package com.triphuddle.api.group.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.group.GroupInvitationAcceptedContract;
import com.triphuddle.amqp.group.GroupInvitationAcceptedEvent;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserEmailVerifiedEvent;
import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.GroupInvitationTokenRequest;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.messaging.UserEventListener;
import com.triphuddle.api.group.security.GroupMatrices;
import com.triphuddle.testsupport.api.AbstractCrudApiIT;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupInvitationApiIT extends AbstractCrudApiIT<CreateGroupInvitationRequest, UpdateGroupInvitationRequest> {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserEventListener userEvents;

    private UUID groupId;

    @BeforeEach
    void createAGroupTheActorAdministers() throws Exception {
        groupId = idOf(body(send(post("/api/v1/groups"), GroupFixtures.createGroup().build(), actor())
                .andExpect(status().isCreated())));
    }

    @Override
    protected String basePath() {
        return "/api/v1/groups/invitations";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return GroupMatrices.GROUP_PARTS;
    }

    @Override
    protected CreateGroupInvitationRequest validCreate() {
        return GroupFixtures.createInvitation(groupId).build();
    }

    @Override
    protected UpdateGroupInvitationRequest validUpdate(UUID id) {
        return GroupFixtures.updateInvitation(id, GroupRole.ADMIN);
    }

    @Override
    protected Map<String, CreateGroupInvitationRequest> invalidCreates() {
        return Map.of(
                "no group", GroupFixtures.createInvitation(groupId).groupId(null).build(),
                "not an email", GroupFixtures.createInvitation(groupId).email("friend-at-example.com").build(),
                "blank email", GroupFixtures.createInvitation(groupId).email(" ").build(),
                "email over 255 characters", GroupFixtures.createInvitation(groupId)
                        .email("a".repeat(250) + "@example.com").build());
    }

    @Override
    protected Optional<CreateGroupInvitationRequest> clashingCreate(CreateGroupInvitationRequest existing) {
        return Optional.of(GroupFixtures.createInvitation(groupId).email(existing.getEmail().toUpperCase()).build());
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateGroupInvitationRequest update) {
        assertThat(after.path("role").asText()).isEqualTo("ADMIN");
        assertThat(after.path("status").asText()).isEqualTo("PENDING");
    }

    @Override
    protected int unknownIdStatus(Operation operation) {
        return operation == Operation.DELETE ? 403 : 404;
    }

    // --- the invitee's answer, by token, with or without signing in -----------------------------------------------

    @Test
    void anInviteeAcceptsWithTheTokenFromTheEmailWithoutSigningIn() throws Exception {
        String email = GroupFixtures.freshEmail();
        UUID token = invite(email);

        answer("accept", token, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(published.published()).anySatisfy(p -> {
            assertThat(p.payload()).isInstanceOf(GroupInvitationAcceptedEvent.class);
            assertThat(p.routingKey()).isEqualTo(GroupInvitationAcceptedContract.GROUP_INVITATION_ACCEPTED_QUEUE_NAME);
        });
    }

    @Test
    void someoneSignedInWithAnotherAddressCannotAccept() throws Exception {
        UUID token = invite(GroupFixtures.freshEmail());

        answer("accept", token, "someone-else@example.com").andExpect(status().isForbidden());
    }

    @Test
    void anInvitationCanBeAnsweredOnlyOnceAndNotOnceCanceled() throws Exception {
        UUID rejected = invite(GroupFixtures.freshEmail());
        answer("reject", rejected, null).andExpect(status().isOk());
        answer("accept", rejected, null).andExpect(status().isUnprocessableEntity());

        String email = GroupFixtures.freshEmail();
        UUID canceledToken = invite(email);
        mvc.perform(post(basePath() + "/" + invitationIdOf(canceledToken) + "/cancel").with(actor()))
                .andExpect(status().isOk());
        answer("accept", canceledToken, email).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no longer valid")));
    }

    @Test
    void anInvitationExpiresAfterSevenDays() throws Exception {
        UUID token = invite(GroupFixtures.freshEmail());
        jdbc.update("update group_invitations set expires_at = now() - interval '1 second' where token = ?", token);

        answer("accept", token, null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aRejectedAddressCanBeInvitedAgain() throws Exception {
        String email = GroupFixtures.freshEmail();
        answer("reject", invite(email), null).andExpect(status().isOk());

        send(post(basePath()), GroupFixtures.createInvitation(groupId).email(email).build(), actor())
                .andExpect(status().isCreated());
    }

    @Test
    void anUnknownTokenIsNotFound() throws Exception {
        answer("accept", UUID.randomUUID(), null).andExpect(status().isNotFound());
    }

    @Test
    void theInviteeIsEmailedTheLinks() throws Exception {
        String email = GroupFixtures.freshEmail();
        UUID token = invite(email);

        assertThat(published.published()).filteredOn(p -> p.payload() instanceof SendEmailRequest)
                .singleElement().satisfies(p -> {
                    SendEmailRequest request = (SendEmailRequest) p.payload();
                    assertThat(p.routingKey()).isEqualTo(NotificationEmailContract.EMAIL_QUEUE_NAME);
                    assertThat(request.recipientEmail()).isEqualTo(email);
                    assertThat((String) request.templateData().get("acceptUrl")).endsWith("token=" + token);
                });
    }

    // --- GRP-4: the membership arrives once a verified user has the invited email ---------------------------------

    @Test
    void anAcceptedInvitationBecomesAMembershipWhenTheInviteeVerifiesTheirEmail() throws Exception {
        String email = GroupFixtures.freshEmail();
        UUID token = invite(email);
        answer("accept", token, null).andExpect(status().isOk());
        UUID invitee = UUID.randomUUID();

        userEvents.onUserEmailVerified(new UserEmailVerifiedEvent(invitee, email.toUpperCase()));
        userEvents.onUserEmailVerified(new UserEmailVerifiedEvent(invitee, email));

        JsonNode members = body(mvc.perform(get("/api/v1/groups/memberships/group/" + groupId)
                .param("page", "1").param("pageSize", "10").with(actor())).andExpect(status().isOk()));
        assertThat(members.path("data").findValuesAsText("userId")).containsOnlyOnce(invitee.toString());
        mvc.perform(get(basePath() + "/" + invitationIdOf(token)).with(actor()))
                .andExpect(jsonPath("$.acceptedByUserId").value(invitee.toString()));
    }

    @Test
    void aGroupsAdminsListItsInvitationsAndStrangersCannot() throws Exception {
        String email = GroupFixtures.freshEmail();
        UUID invitation = invitationIdOf(invite(email));

        JsonNode page = body(mvc.perform(get(basePath() + "/group/" + groupId).param("page", "1")
                .param("pageSize", "10").with(actor())).andExpect(status().isOk()));
        assertThat(page.path("data").findValuesAsText("id")).containsExactly(invitation.toString());
        assertThat(page.path("data").get(0).path("email").asText()).isEqualTo(email);
        mvc.perform(get(basePath() + "/group/" + groupId).param("page", "1").param("pageSize", "10")
                .with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
    }

    @Test
    void theDatabaseRefusesASecondPendingInvitationForAnAddress() {
        String insert = """
                insert into group_invitations (id, group_id, email, role, token, status, expires_at,
                    invited_by_user_id, created_at, updated_at)
                values (gen_random_uuid(), ?, 'dup@example.com', 'MEMBER', gen_random_uuid(), 'PENDING',
                    now() + interval '7 days', gen_random_uuid(), now(), now())""";
        jdbc.update(insert, groupId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(insert, groupId))
                .hasMessageContaining("uk_group_invitation_pending_email");
    }

    private UUID invite(String email) throws Exception {
        published.reset();
        JsonNode invitation = body(send(post(basePath()), GroupFixtures.createInvitation(groupId).email(email).build(),
                actor()).andExpect(status().isCreated()));
        return jdbc.queryForObject("select token from group_invitations where id = ?", UUID.class,
                idOf(invitation));
    }

    private UUID invitationIdOf(UUID token) {
        return jdbc.queryForObject("select id from group_invitations where token = ?", UUID.class, token);
    }

    private ResultActions answer(String action, UUID token, String signedInEmail) throws Exception {
        var request = post(basePath() + "/" + action)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new GroupInvitationTokenRequest(token)));
        if (signedInEmail != null) {
            request.with(jwt().jwt(j -> j.claim("userId", UUID.randomUUID().toString()).claim("email", signedInEmail)));
        }
        return mvc.perform(request);
    }
}
