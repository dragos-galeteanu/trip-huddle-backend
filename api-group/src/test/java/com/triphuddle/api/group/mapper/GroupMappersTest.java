package com.triphuddle.api.group.mapper;

import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupMappersTest {

    private final GroupMapper groups = Mappers.getMapper(GroupMapper.class);
    private final GroupMembershipMapper memberships = Mappers.getMapper(GroupMembershipMapper.class);
    private final GroupInvitationMapper invitations = Mappers.getMapper(GroupInvitationMapper.class);

    @Test
    void anUpdateNeverReKeysTheRowOrChangesWhatOnlyTheServiceSets() {
        Group group = GroupFixtures.group();
        UUID id = group.getId();
        UUID creator = group.getCreatedByUserId();
        groups.updateEntityFromRequest(group, GroupFixtures.updateGroup(UUID.randomUUID()).build());

        GroupMembership membership = GroupFixtures.membership(group, GroupRole.MEMBER);
        UUID member = membership.getUserId();
        memberships.updateEntityFromRequest(membership, GroupFixtures.updateMembership(UUID.randomUUID(),
                GroupRole.ADMIN));

        GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.PENDING);
        String email = invitation.getEmail();
        UUID token = invitation.getToken();
        invitations.updateEntityFromRequest(invitation, GroupFixtures.updateInvitation(UUID.randomUUID(),
                GroupRole.ADMIN));

        assertThat(group.getId()).isEqualTo(id);
        assertThat(group.getCreatedByUserId()).isEqualTo(creator);
        assertThat(membership.getUserId()).isEqualTo(member);
        assertThat(membership.getRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(invitation.getEmail()).isEqualTo(email);
        assertThat(invitation.getToken()).isEqualTo(token);
        assertThat(invitation.getStatus()).isEqualTo(GroupInvitationStatus.PENDING);
    }

    @Test
    void exposesTheGroupByIdAndNeverTheToken() {
        Group group = GroupFixtures.group();

        assertThat(memberships.toResponse(GroupFixtures.membership(group, GroupRole.ADMIN)).groupId())
                .isEqualTo(group.getId());
        assertThat(invitations.toResponse(GroupFixtures.invitation(group, GroupInvitationStatus.PENDING)))
                .hasNoNullFieldsOrPropertiesExcept("acceptedByUserId", "createdAt", "updatedAt", "disabledAt",
                        "deletedAt")
                .extracting("groupId").isEqualTo(group.getId());
        assertThat(GroupInvitationResponseFields.names()).doesNotContain("token");
    }

    @Test
    void takesCreateRequestsAsTheyCome() {
        Group group = new Group();
        groups.createEntityFromRequest(group, GroupFixtures.createGroup().name("Rome").description("Spring").build());

        GroupInvitation invitation = new GroupInvitation();
        invitations.createEntityFromRequest(invitation, GroupFixtures.createInvitation(UUID.randomUUID())
                .role(GroupRole.ADMIN).build());

        assertThat(group.getName()).isEqualTo("Rome");
        assertThat(group.getDescription()).isEqualTo("Spring");
        assertThat(invitation.getRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(groups.toResponse(null)).isNull();
        assertThat(memberships.toResponse(null)).isNull();
        assertThat(invitations.toResponse(null)).isNull();
    }

    /** The response's component names, so the test fails if a token is ever added to it. */
    private static final class GroupInvitationResponseFields {
        static java.util.List<String> names() {
            return java.util.Arrays.stream(com.triphuddle.api.group.dto.GroupInvitationResponse.class
                    .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        }
    }
}
