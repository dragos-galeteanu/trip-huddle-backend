package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.TicketCategory;
import com.triphuddle.api.chat.entity.TicketPriority;
import com.triphuddle.api.chat.entity.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A support ticket with its participants; the priority is only shown to staff.
 *
 * @param id the ID of the ticket
 * @param number the ticket number
 * @param reference the number people quote, such as TH-10234
 * @param conversationId the ID of the support conversation
 * @param requesterId the customer the ticket is for
 * @param openedByUserId the customer, or the staff member who opened it towards them
 * @param assigneeId the staff member handling it, or null if unclaimed
 * @param subject the subject of the ticket
 * @param category what the ticket is about
 * @param priority how urgent it is, or null for the customer
 * @param status where the ticket stands
 * @param groupId the group it concerns, or null
 * @param linkedConversationId the conversation it concerns, or null
 * @param messageId the message it concerns, or null
 * @param messageReportId the message report it concerns, or null
 * @param firstResponseAt when staff first replied, or null
 * @param resolvedAt when it was last resolved, or null
 * @param closedAt when it was closed, or null while not closed
 * @param closedByUserId who closed it, or null
 * @param rating the customer's 1-5 rating of the support, or null if not rated
 * @param ratingComment the customer's comment with the rating, or null
 * @param ratedAt when the customer last rated it, or null
 * @param participants the customer and the staff, including those who left
 * @param createdAt when it was opened
 * @param updatedAt when it last changed
 */
public record SupportTicketResponse(
        UUID id,
        long number,
        String reference,
        UUID conversationId,
        UUID requesterId,
        UUID openedByUserId,
        UUID assigneeId,
        String subject,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        UUID groupId,
        UUID linkedConversationId,
        String messageId,
        UUID messageReportId,
        Instant firstResponseAt,
        Instant resolvedAt,
        Instant closedAt,
        UUID closedByUserId,
        Integer rating,
        String ratingComment,
        Instant ratedAt,
        List<ParticipantResponse> participants,
        Instant createdAt,
        Instant updatedAt
) {
}
