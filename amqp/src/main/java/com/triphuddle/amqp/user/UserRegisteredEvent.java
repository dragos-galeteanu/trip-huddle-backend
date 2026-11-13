package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user registers, before their email address is verified.
 *
 * @param userId the ID of the user
 */
public record UserRegisteredEvent(UUID userId) {
}
