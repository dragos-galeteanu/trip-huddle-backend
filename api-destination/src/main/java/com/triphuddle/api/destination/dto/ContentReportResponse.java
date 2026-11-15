package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.ContentSection;

import java.time.Instant;
import java.util.UUID;

/**
 * A report of a section, for staff.
 *
 * @param id the ID of the report
 * @param placeId the place
 * @param placeName its name
 * @param section the section
 * @param languageCode the language reported
 * @param reason why the reader reported it
 * @param reportedByUserId who reported it
 * @param reportedAt when
 * @param resolution KEPT or REGENERATED, or null while open
 * @param resolvedByUserId who resolved it, or null
 * @param resolvedAt when, or null
 */
public record ContentReportResponse(
        UUID id,
        UUID placeId,
        String placeName,
        ContentSection section,
        String languageCode,
        String reason,
        UUID reportedByUserId,
        Instant reportedAt,
        String resolution,
        UUID resolvedByUserId,
        Instant resolvedAt
) {
}
