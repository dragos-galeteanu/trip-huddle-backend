package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a support ticket moves to another status.
 *
 * @param ticketId the ID of the ticket
 * @param reference the number people quote, such as TH-10234
 * @param conversationId the ID of the support conversation
 * @param requesterId the ID of the customer
 * @param actorId who changed it, or null if the system did
 * @param fromStatus the status before
 * @param toStatus the status after
 * @param subject the subject of the ticket
 */
public record SupportTicketStatusChangedEvent(
        UUID ticketId,
        String reference,
        UUID conversationId,
        UUID requesterId,
        UUID actorId,
        String fromStatus,
        String toStatus,
        String subject
) {
}
