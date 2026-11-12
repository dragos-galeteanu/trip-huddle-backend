package com.triphuddle.api.user.dto;

import java.time.Instant;
import java.util.UUID;

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
