package com.triphuddle.api.user.dto;

import com.triphuddle.common.security.UserRole;

import java.time.Instant;
import java.util.UUID;

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
