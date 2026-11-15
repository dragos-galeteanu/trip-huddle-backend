package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.ReportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A report of a message; staff also get the message itself, even if it was deleted.
 *
 * @param id the ID of the report
 * @param messageId the ID of the reported message
 * @param conversationId the ID of the conversation
 * @param reporterId the ID of the user who reported it
 * @param reason why it was reported
 * @param status the status of the review
 * @param resolvedByUserId the staff member who resolved it, or null
 * @param resolvedAt when it was resolved, or null
 * @param resolutionNote the note left by the staff member, or null
 * @param message the reported message, or null when the reporter reads their own report
 * @param createdAt when it was reported
 */
public record MessageReportResponse(
        UUID id,
        String messageId,
        UUID conversationId,
        UUID reporterId,
        String reason,
        ReportStatus status,
        UUID resolvedByUserId,
        Instant resolvedAt,
        String resolutionNote,
        MessageResponse message,
        Instant createdAt
) {
}
