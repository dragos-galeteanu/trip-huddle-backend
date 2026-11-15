package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when staff reply to the customer after the customer last wrote, not for every staff message.
 *
 * @param ticketId the ID of the ticket
 * @param reference the number people quote, such as TH-10234
 * @param conversationId the ID of the support conversation
 * @param requesterId the ID of the customer
 * @param staffUserId the ID of the staff member who replied
 * @param messageId the ID of the reply
 * @param subject the subject of the ticket
 */
public record SupportTicketRepliedEvent(
        UUID ticketId,
        String reference,
        UUID conversationId,
        UUID requesterId,
        UUID staffUserId,
        String messageId,
        String subject
) {
}
