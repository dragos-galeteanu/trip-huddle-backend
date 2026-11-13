package com.triphuddle.amqp.group;

import java.util.UUID;

/**
 * Published when a group invitation is disabled.
 *
 * @param groupInvitationId the ID of the group invitation
 * @param groupId the ID of the group
 * @param email the invited email address
 * @param role the role the invitee gets in the group
 */
public record GroupInvitationDisabledEvent(UUID groupInvitationId, UUID groupId, String email, String role) {
}
