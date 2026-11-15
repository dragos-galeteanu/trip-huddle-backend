package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user is deleted.
 *
 * @param userId the ID of the user
 */
public record UserDeletedEvent(UUID userId) {
}
