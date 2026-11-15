package com.triphuddle.api.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A user the caller blocks.
 *
 * @param blockedUserId the ID of the blocked user
 * @param createdAt when the block was placed
 */
public record UserBlockResponse(UUID blockedUserId, Instant createdAt) {
}
