package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a user reports a chat message for review.
 *
 * @param messageReportId the ID of the report
 * @param messageId the ID of the reported message
 * @param conversationId the ID of the conversation
 * @param reporterId the ID of the user who reported it
 */
public record ChatMessageReportedEvent(UUID messageReportId, String messageId, UUID conversationId, UUID reporterId) {
}
