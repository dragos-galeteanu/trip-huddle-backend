package com.triphuddle.user.dto;

import com.triphuddle.common.security.UserRole;

import java.time.Instant;
import java.util.UUID;

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
