package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.TicketCategory;
import com.triphuddle.api.chat.entity.TicketStatus;

import java.util.UUID;

/**
 * The ticket of a support conversation, as shown in the conversation list.
 *
 * @param id the ID of the ticket
 * @param reference the number people quote, such as TH-10234
 * @param subject the subject of the ticket
 * @param category what the ticket is about
 * @param status where the ticket stands
 * @param assigneeId the staff member handling it, or null if unclaimed
 */
public record SupportTicketSummary(
        UUID id,
        String reference,
        String subject,
        TicketCategory category,
        TicketStatus status,
        UUID assigneeId
) {
}
