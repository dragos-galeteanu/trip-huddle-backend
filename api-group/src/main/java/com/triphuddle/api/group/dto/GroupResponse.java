package com.triphuddle.api.group.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing a group.
 *
 * @param id the ID of the group
 * @param name the name of the group
 * @param description the description of the group
 * @param createdByUserId the ID of the user who created the group
 * @param createdAt when the group was created
 * @param updatedAt when the group was last updated
 * @param disabledAt when the group was disabled, or null if it is enabled
 * @param deletedAt when the group was deleted, or null if it was not
 */
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
