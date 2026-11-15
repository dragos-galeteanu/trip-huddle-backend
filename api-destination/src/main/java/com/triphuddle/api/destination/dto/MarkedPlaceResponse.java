package com.triphuddle.api.destination.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A place in a user's favourites or visited list.
 *
 * @param place the place
 * @param visitedOn when they went, if they said
 * @param markedAt when they marked it
 */
public record MarkedPlaceResponse(PlaceSummaryResponse place, LocalDate visitedOn, Instant markedAt) {
}
