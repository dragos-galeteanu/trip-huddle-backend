package com.triphuddle.api.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record LanguageResponse(
        UUID id,
        String code,
        String name,
        String nativeName,
        boolean isRightToLeft,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
