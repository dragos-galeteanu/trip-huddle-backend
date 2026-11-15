package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a chat message is deleted.
 *
 * @param messageId the ID of the message
 * @param conversationId the ID of the conversation
 * @param deletedByUserId the ID of the user who deleted it: the sender, or a group Admin
 */
public record ChatMessageDeletedEvent(String messageId, UUID conversationId, UUID deletedByUserId) {
}
