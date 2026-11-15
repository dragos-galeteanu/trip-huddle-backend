package com.triphuddle.api.destination.repository;

import java.util.UUID;

/**
 * A place a search found.
 *
 * @param placeId the ID of the place
 * @param score how well its best name matched the words, 0 to 1, or null without words
 * @param distanceMeters how far it is from the searched point, or null without one
 */
public record PlaceSearchHit(UUID placeId, Double score, Double distanceMeters) {
}
