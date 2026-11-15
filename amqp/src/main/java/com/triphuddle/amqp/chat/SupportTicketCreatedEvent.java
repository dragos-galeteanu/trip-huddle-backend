package com.triphuddle.amqp.chat;

import java.util.UUID;

/**
 * Published when a support ticket is opened, by the customer or by staff towards them.
 *
 * @param ticketId the ID of the ticket
 * @param reference the number people quote, such as TH-10234
 * @param conversationId the ID of the support conversation
 * @param requesterId the ID of the customer
 * @param openedByUserId the ID of the customer, or of the staff member who opened it towards them
 * @param subject the subject of the ticket
 * @param category the category of the ticket
 */
public record SupportTicketCreatedEvent(
        UUID ticketId,
        String reference,
        UUID conversationId,
        UUID requesterId,
        UUID openedByUserId,
        String subject,
        String category
) {
}
