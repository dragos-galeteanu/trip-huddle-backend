package com.triphuddle.amqp.group;

import java.util.UUID;

/**
 * Published when a group membership is deleted.
 *
 * @param groupMembershipId the ID of the group membership
 * @param groupId the ID of the group
 * @param userId the ID of the user who is the member
 * @param role the role of the member in the group
 */
public record GroupMembershipDeletedEvent(UUID groupMembershipId, UUID groupId, UUID userId, String role) {
}
