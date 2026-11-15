package com.triphuddle.amqp.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user registers or changes their email address or name; carries the whole contact, so a
 * consumer can apply the latest one it sees and drop older ones.
 *
 * @param userId the ID of the user
 * @param email the email address
 * @param firstName the first name
 * @param lastName the last name
 * @param isEmailVerified whether the email address is verified
 * @param changedAt when the change happened, to order events
 */
public record UserContactUpdatedEvent(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        boolean isEmailVerified,
        Instant changedAt
) {
}
