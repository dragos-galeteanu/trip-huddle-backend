package com.triphuddle.api.chat.dto;

/**
 * The kinds of real-time event a client receives on its user topic, or staff on the support queue topic.
 */
public enum ChatEventType {
    MESSAGE_CREATED,
    MESSAGE_UPDATED,
    MESSAGE_DELETED,
    REACTIONS_UPDATED,
    READ_RECEIPT,
    TYPING,
    CONVERSATION_UPDATED,
    // Only on the support queue topic, with the ticket as staff see it.
    TICKET_UPDATED
}
