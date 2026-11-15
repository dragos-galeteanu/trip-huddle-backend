package com.triphuddle.api.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A participant read a conversation up to a message.
 *
 * @param userId the ID of the user who read it
 * @param messageId the ID of the last message they read
 * @param readAt when they read it
 */
public record ReadReceiptPayload(UUID userId, String messageId, Instant readAt) {
}
