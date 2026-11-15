package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A conversation as the calling user sees it, with their unread count and mute state.
 *
 * @param id the ID of the conversation
 * @param type PRIVATE, GROUP or SUPPORT
 * @param groupId the ID of the group, or null if not a group chat
 * @param name the name of the group or the subject of the ticket, or null for a private chat
 * @param isReadOnly whether the caller can no longer send: the group is gone, the ticket closed or they left
 * @param lastMessageId the ID of the last message the caller can see, or null if none
 * @param lastMessagePreview the start of that message, or null if none or deleted
 * @param lastMessageSenderId the sender of that message, or null if none
 * @param lastMessageAt when that message was sent, or null if none
 * @param lastActivityAt the time the caller's conversation list is ordered by
 * @param unreadCount how many messages the caller has not read
 * @param isMuted whether the caller muted the conversation
 * @param mutedUntil when the mute ends, or null if it lasts until undone
 * @param lastReadMessageId the ID of the last message the caller read, or null if none
 * @param leftAt when the caller left, or null if they are still in it
 * @param participants every participant, including those who left
 * @param ticket the ticket of a support chat, or null for other types
 * @param createdAt when the conversation was created
 */
public record ConversationResponse(
        UUID id,
        ConversationType type,
        UUID groupId,
        String name,
        boolean isReadOnly,
        String lastMessageId,
        String lastMessagePreview,
        UUID lastMessageSenderId,
        Instant lastMessageAt,
        Instant lastActivityAt,
        int unreadCount,
        boolean isMuted,
        Instant mutedUntil,
        String lastReadMessageId,
        Instant leftAt,
        List<ParticipantResponse> participants,
        SupportTicketSummary ticket,
        Instant createdAt
) {
}
