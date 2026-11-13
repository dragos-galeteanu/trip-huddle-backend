package com.triphuddle.api.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing an user profile.
 *
 * @param id the ID of the user profile
 * @param userId the ID of the user
 * @param bio the biography of the user
 * @param countryCode the code of the country of the user
 * @param profilePictureUrl the URL of the profile picture
 * @param coverPictureUrl the URL of the cover picture
 * @param xUrl the URL of the X account
 * @param facebookUrl the URL of the Facebook account
 * @param instagramUrl the URL of the Instagram account
 * @param websiteUrl the URL of the personal website
 * @param createdAt when the user profile was created
 * @param updatedAt when the user profile was last updated
 * @param disabledAt when the user profile was disabled, or null if it is enabled
 * @param deletedAt when the user profile was deleted, or null if it was not
 */
public record UserProfileResponse(
        UUID id,
        UUID userId,
        String bio,
        String countryCode,
        String profilePictureUrl,
        String coverPictureUrl,
        String xUrl,
        String facebookUrl,
        String instagramUrl,
        String websiteUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
