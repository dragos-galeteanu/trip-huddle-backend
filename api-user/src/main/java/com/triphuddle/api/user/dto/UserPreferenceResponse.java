package com.triphuddle.api.user.dto;

import java.time.Instant;
import java.util.UUID;

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
