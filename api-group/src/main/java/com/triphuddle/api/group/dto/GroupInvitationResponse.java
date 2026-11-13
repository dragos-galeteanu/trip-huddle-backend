package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupRole;

import java.time.Instant;
import java.util.UUID;

// The token is deliberately absent: it travels only in the invitation email.
/**
 * Response body describing a group invitation.
 *
 * @param id the ID of the group invitation
 * @param groupId the ID of the group
 * @param email the invited email address
 * @param role the role the invitee gets in the group
 * @param status the status of the invitation
 * @param expiresAt when the invitation stops being answerable
 * @param invitedByUserId the ID of the user who sent the invitation
 * @param acceptedByUserId the ID of the user who became a member through the invitation, or null if none yet
 * @param createdAt when the group invitation was created
 * @param updatedAt when the group invitation was last updated
 * @param disabledAt when the group invitation was disabled, or null if it is enabled
 * @param deletedAt when the group invitation was deleted, or null if it was not
 */
public record GroupInvitationResponse(
        UUID id,
        UUID groupId,
        String email,
        GroupRole role,
        GroupInvitationStatus status,
        Instant expiresAt,
        UUID invitedByUserId,
        UUID acceptedByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
