package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a staff member disables a chat message.
 *
 * @param messageId the ID of the message
 * @param conversationId the ID of the conversation
 * @param disabledByUserId the ID of the staff member who disabled it
 */
public record ChatMessageDisabledEvent(String messageId, UUID conversationId, UUID disabledByUserId) {
}
