package com.triphuddle.api.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope of every real-time event; on {@code CONVERSATION_UPDATED} the client refetches the conversation.
 *
 * @param type the kind of event
 * @param conversationId the ID of the conversation it concerns
 * @param payload a {@link MessageResponse}, {@link ReadReceiptPayload}, {@link TypingPayload} or null
 * @param occurredAt when it happened
 */
public record ChatEvent(ChatEventType type, UUID conversationId, Object payload, Instant occurredAt) {
}
