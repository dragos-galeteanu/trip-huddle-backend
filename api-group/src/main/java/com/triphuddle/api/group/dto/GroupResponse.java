package com.triphuddle.api.group.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
