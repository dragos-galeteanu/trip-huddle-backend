package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.ParticipantRole;

import java.time.Instant;
import java.util.UUID;

/**
 * A participant of a conversation, with how far they have read for "seen by" displays.
 *
 * @param userId the ID of the user
 * @param role the role of the user in the conversation
 * @param joinedAt when the user joined
 * @param leftAt when the user left, or null if they are still in it
 * @param lastReadMessageId the ID of the last message the user read, or null if none
 * @param lastReadAt when the user last marked the conversation read, or null if never
 */
public record ParticipantResponse(
        UUID userId,
        ParticipantRole role,
        Instant joinedAt,
        Instant leftAt,
        String lastReadMessageId,
        Instant lastReadAt
) {
}
