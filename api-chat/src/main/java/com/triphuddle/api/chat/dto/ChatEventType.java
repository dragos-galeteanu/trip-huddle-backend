package com.triphuddle.api.chat.dto;

/**
 * The kinds of real-time event a client receives on its user topic.
 */
public enum ChatEventType {
    MESSAGE_CREATED,
    MESSAGE_UPDATED,
    MESSAGE_DELETED,
    REACTIONS_UPDATED,
    READ_RECEIPT,
    TYPING,
    CONVERSATION_UPDATED
}
