package com.triphuddle.api.chat.dto;

import java.util.UUID;

/**
 * A participant started or stopped typing.
 *
 * @param userId the ID of the user
 * @param isTyping whether they are typing
 */
public record TypingPayload(UUID userId, boolean isTyping) {
}
