package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when an accepted group invitation's email belongs to a user with a verified email address.
 *
 * @param groupInvitationId the ID of the accepted group invitation
 * @param userId the ID of the user the invited email belongs to
 */
public record UserMatchedToGroupInvitationEvent(UUID groupInvitationId, UUID userId) {
}
