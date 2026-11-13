package com.triphuddle.api.user.dto;

import com.triphuddle.common.security.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing an user.
 *
 * @param id the ID of the user
 * @param firstName the first name of the user
 * @param lastName the last name of the user
 * @param role the platform role of the user
 * @param createdAt when the user was created
 * @param disabledAt when the user was disabled, or null if it is enabled
 * @param deletedAt when the user was deleted, or null if it was not
 */
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        UserRole role,
        Instant createdAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
