package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user is disabled.
 *
 * @param userId the ID of the user
 */
public record UserDisabledEvent(UUID userId) {
}
