package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupRole;

import java.time.Instant;
import java.util.UUID;

public record GroupMembershipResponse(
        UUID id,
        UUID groupId,
        UUID userId,
        GroupRole role,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
