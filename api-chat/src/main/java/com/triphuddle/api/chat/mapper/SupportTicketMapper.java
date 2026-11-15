package com.triphuddle.api.chat.mapper;

import com.triphuddle.api.chat.dto.SupportTicketEventResponse;
import com.triphuddle.api.chat.dto.SupportTicketResponse;
import com.triphuddle.api.chat.dto.SupportTicketSummary;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.SupportTicket;
import com.triphuddle.api.chat.entity.SupportTicketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps {@link SupportTicket} and its history to their response DTOs.
 */
@Component
@RequiredArgsConstructor
public class SupportTicketMapper {

    private final ParticipantMapper participantMapper;

    /**
     * Converts a ticket to its response DTO, hiding the priority from the customer.
     *
     * @param ticket the ticket
     * @param participants the participants of its conversation
     * @param isStaffView whether staff are looking at it
     * @return the response DTO
     */
    public SupportTicketResponse toResponse(SupportTicket ticket, List<ConversationParticipant> participants,
                                            boolean isStaffView) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getNumber(),
                ticket.getReference(),
                ticket.getConversation().getId(),
                ticket.getRequesterId(),
                ticket.getOpenedByUserId(),
                ticket.getAssigneeId(),
                ticket.getSubject(),
                ticket.getCategory(),
                isStaffView ? ticket.getPriority() : null,
                ticket.getStatus(),
                ticket.getGroupId(),
                ticket.getLinkedConversationId(),
                ticket.getMessageId(),
                ticket.getMessageReportId(),
                ticket.getFirstResponseAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                ticket.getClosedByUserId(),
                ticket.getRating(),
                ticket.getRatingComment(),
                ticket.getRatedAt(),
                participantMapper.toResponseList(participants),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    /**
     * Converts a ticket to the summary a conversation carries.
     *
     * @param ticket the ticket, or null
     * @return the summary, or null if there is no ticket
     */
    public SupportTicketSummary toSummary(SupportTicket ticket) {
        if (ticket == null) {
            return null;
        }
        return new SupportTicketSummary(ticket.getId(), ticket.getReference(), ticket.getSubject(),
                ticket.getCategory(), ticket.getStatus(), ticket.getAssigneeId());
    }

    /**
     * Converts a ticket's history to response DTOs.
     *
     * @param events the events, oldest first
     * @return the response DTOs
     */
    public List<SupportTicketEventResponse> toEventResponseList(List<SupportTicketEvent> events) {
        return events.stream()
                .map(event -> new SupportTicketEventResponse(event.getId(), event.getType(), event.getActorId(),
                        event.getTargetUserId(), event.getFromValue(), event.getToValue(), event.getCreatedAt()))
                .toList();
    }
}
