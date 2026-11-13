package com.triphuddle.api.user.dto;

import com.triphuddle.common.security.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing an user, including private account details.
 *
 * @param id the ID of the user
 * @param firebaseUid the Firebase UID of the user
 * @param email the email address of the user
 * @param phoneNumber the phone number of the user
 * @param firstName the first name of the user
 * @param lastName the last name of the user
 * @param role the platform role of the user
 * @param emailVerified whether the email address was verified
 * @param createdAt when the user was created
 * @param updatedAt when the user was last updated
 * @param disabledAt when the user was disabled, or null if it is enabled
 * @param deletedAt when the user was deleted, or null if it was not
 */
public record DetailedUserResponse(
        UUID id,
        String firebaseUid,
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        UserRole role,
        boolean emailVerified,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
