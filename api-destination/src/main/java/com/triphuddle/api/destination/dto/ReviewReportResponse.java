package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.ReviewReportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A report of a review, as staff see it.
 *
 * @param id the ID of the report
 * @param review the review reported
 * @param reporterId who reported it
 * @param reason why
 * @param status OPEN, KEPT or HIDDEN
 * @param createdAt when it was reported
 * @param resolvedByUserId the staff member who settled it
 * @param resolvedAt when
 */
public record ReviewReportResponse(UUID id, ReviewResponse review, UUID reporterId, String reason,
                                   ReviewReportStatus status, Instant createdAt, UUID resolvedByUserId,
                                   Instant resolvedAt) {
}
