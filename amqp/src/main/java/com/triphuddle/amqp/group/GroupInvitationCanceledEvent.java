package com.triphuddle.amqp.group;

import java.util.UUID;

/**
 * Published when a group invitation is canceled by the group's side before the invitee answers.
 *
 * @param groupInvitationId the ID of the group invitation
 * @param groupId the ID of the group
 * @param email the invited email address
 * @param role the role the invitee would have got in the group
 */
public record GroupInvitationCanceledEvent(UUID groupInvitationId, UUID groupId, String email, String role) {
}
