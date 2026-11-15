package com.triphuddle.api.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * One emoji's reactions to a message.
 *
 * @param emoji the emoji
 * @param count how many users reacted with it
 * @param userIds the IDs of those users
 */
public record ReactionResponse(String emoji, int count, List<UUID> userIds) {
}
