package com.triphuddle.api.group.fixtures;

import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.entity.GroupRole;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valid groups, memberships, invitations and requests, changed by a test only where it cares.
 */
public final class GroupFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private GroupFixtures() {
    }

    public static CreateGroupRequest.CreateGroupRequestBuilder createGroup() {
        return CreateGroupRequest.builder().name("Lisbon trip " + SEQ.incrementAndGet()).description("Five days");
    }

    public static UpdateGroupRequest.UpdateGroupRequestBuilder updateGroup(UUID id) {
        return UpdateGroupRequest.builder().id(id).name("Porto trip " + SEQ.incrementAndGet()).description("Changed");
    }

    public static CreateGroupMembershipRequest.CreateGroupMembershipRequestBuilder createMembership(UUID groupId) {
        return CreateGroupMembershipRequest.builder().groupId(groupId).userId(UUID.randomUUID()).role(GroupRole.MEMBER);
    }

    public static UpdateGroupMembershipRequest updateMembership(UUID id, GroupRole role) {
        return UpdateGroupMembershipRequest.builder().id(id).role(role).build();
    }

    public static CreateGroupInvitationRequest.CreateGroupInvitationRequestBuilder createInvitation(UUID groupId) {
        return CreateGroupInvitationRequest.builder().groupId(groupId).email(freshEmail()).role(GroupRole.MEMBER);
    }

    public static UpdateGroupInvitationRequest updateInvitation(UUID id, GroupRole role) {
        return UpdateGroupInvitationRequest.builder().id(id).role(role).build();
    }

    public static String freshEmail() {
        return "friend" + SEQ.incrementAndGet() + "@example.com";
    }

    public static Group group() {
        Group group = new Group();
        group.setId(UUID.randomUUID());
        group.setName("Lisbon trip " + SEQ.incrementAndGet());
        group.setCreatedByUserId(UUID.randomUUID());
        group.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        group.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return group;
    }

    public static GroupMembership membership(Group group, GroupRole role) {
        GroupMembership membership = new GroupMembership();
        membership.setId(UUID.randomUUID());
        membership.setGroup(group);
        membership.setUserId(UUID.randomUUID());
        membership.setRole(role);
        return membership;
    }

    public static GroupInvitation invitation(Group group, GroupInvitationStatus status) {
        GroupInvitation invitation = new GroupInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setGroup(group);
        invitation.setEmail(freshEmail());
        invitation.setRole(GroupRole.MEMBER);
        invitation.setToken(UUID.randomUUID());
        invitation.setStatus(status);
        invitation.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        invitation.setInvitedByUserId(UUID.randomUUID());
        return invitation;
    }
}
