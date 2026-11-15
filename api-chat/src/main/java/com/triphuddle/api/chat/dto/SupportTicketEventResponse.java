package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.SupportTicketEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * One change in a support ticket's history.
 *
 * @param id the ID of the event
 * @param type the kind of change
 * @param actorId who made it, or null if the system did
 * @param targetUserId the user it concerns, or null
 * @param fromValue the value before, or null
 * @param toValue the value after, or null
 * @param createdAt when it happened
 */
public record SupportTicketEventResponse(
        UUID id,
        SupportTicketEventType type,
        UUID actorId,
        UUID targetUserId,
        String fromValue,
        String toValue,
        Instant createdAt
) {
}
