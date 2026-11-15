package com.triphuddle.api.chat.entity;

import java.util.UUID;

/**
 * One user's emoji reaction to a message; a user reacts at most once with each emoji.
 *
 * @param emoji the emoji
 * @param userId the ID of the user who reacted
 */
public record MessageReaction(String emoji, UUID userId) {
}
