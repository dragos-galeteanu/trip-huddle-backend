package com.triphuddle.api.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing an user preference.
 *
 * @param id the ID of the user preference
 * @param userId the ID of the user
 * @param languageCode the code of the preferred language
 * @param countryCode the code of the country of the user
 * @param isMarketingOptIn whether the user accepts marketing messages
 * @param createdAt when the user preference was created
 * @param updatedAt when the user preference was last updated
 * @param disabledAt when the user preference was disabled, or null if it is enabled
 * @param deletedAt when the user preference was deleted, or null if it was not
 */
public record UserPreferenceResponse(
        String id,
        UUID userId,
        String languageCode,
        String countryCode,
        boolean isMarketingOptIn,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
