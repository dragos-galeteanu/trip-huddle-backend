package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a chat message is sent.
 *
 * @param messageId the ID of the message
 * @param conversationId the ID of the conversation
 * @param senderId the ID of the user who sent the message
 */
public record ChatMessageSentEvent(String messageId, UUID conversationId, UUID senderId) {
}
