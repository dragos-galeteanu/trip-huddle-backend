package com.triphuddle.api.destination.dto;

import java.util.Map;
import java.util.UUID;

/**
 * What a place's visible reviews add up to.
 *
 * @param placeId the place
 * @param count how many there are
 * @param averageRating their average, to one decimal, or null without reviews
 * @param byRating how many give each rating, 1 to 5
 */
public record ReviewSummaryResponse(UUID placeId, long count, Double averageRating, Map<Integer, Long> byRating) {
}
