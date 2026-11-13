package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing a group membership.
 *
 * @param id the ID of the group membership
 * @param groupId the ID of the group
 * @param userId the ID of the user
 * @param role the role of the member in the group
 * @param createdAt when the group membership was created
 * @param updatedAt when the group membership was last updated
 * @param disabledAt when the group membership was disabled, or null if it is enabled
 * @param deletedAt when the group membership was deleted, or null if it was not
 */
public record GroupMembershipResponse(
        UUID id,
        UUID groupId,
        UUID userId,
        GroupRole role,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
