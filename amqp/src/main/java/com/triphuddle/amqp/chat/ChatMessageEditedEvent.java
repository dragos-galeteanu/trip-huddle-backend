package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a chat message is edited by its sender.
 *
 * @param messageId the ID of the message
 * @param conversationId the ID of the conversation
 * @param senderId the ID of the user who sent the message
 */
public record ChatMessageEditedEvent(String messageId, UUID conversationId, UUID senderId) {
}
