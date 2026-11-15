package com.triphuddle.api.destination.dto;

/**
 * A place found by meaning.
 *
 * @param place the place
 * @param similarity how close in meaning, from 0 to 1
 */
public record SemanticHitResponse(PlaceSummaryResponse place, double similarity) {
}
