package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupRole;

import java.time.Instant;
import java.util.UUID;

// The token is deliberately absent: it travels only in the invitation email.
public record GroupInvitationResponse(
        UUID id,
        UUID groupId,
        String email,
        GroupRole role,
        GroupInvitationStatus status,
        Instant expiresAt,
        UUID invitedByUserId,
        UUID acceptedByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
