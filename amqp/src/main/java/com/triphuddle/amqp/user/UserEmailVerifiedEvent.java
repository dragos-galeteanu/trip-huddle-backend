package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user verifies their email address.
 *
 * @param userId the ID of the user
 * @param email the verified email address
 */
public record UserEmailVerifiedEvent(UUID userId, String email) {
}
