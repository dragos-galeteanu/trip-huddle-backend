package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.PlaceMarkKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A user's mark on a place.
 *
 * @param placeId the place
 * @param kind FAVOURITE or VISITED
 * @param visitedOn when they went, if they said
 * @param markedAt when they marked it
 */
public record PlaceMarkResponse(UUID placeId, PlaceMarkKind kind, LocalDate visitedOn, Instant markedAt) {
}
