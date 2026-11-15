package com.triphuddle.api.destination.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A review of a place.
 *
 * @param id the ID of the review
 * @param placeId the place
 * @param userId its author
 * @param rating 1 to 5
 * @param title its title, or null
 * @param body its text, or null
 * @param visitedOn when the author went, if they said
 * @param languageCode the language it is written in
 * @param isHidden whether it is hidden; only its author and staff see a hidden one
 * @param hiddenReason why, for its author and staff
 * @param createdAt when it was written
 * @param updatedAt when it last changed
 */
public record ReviewResponse(UUID id, UUID placeId, UUID userId, int rating, String title, String body,
                             LocalDate visitedOn, String languageCode, boolean isHidden, String hiddenReason,
                             Instant createdAt, Instant updatedAt) {
}
