package com.triphuddle.api.destination.dto;

import java.util.List;

/**
 * Places found by meaning.
 *
 * @param state READY, GENERATING (the query or place is being embedded; ask again shortly), FAILED or UNAVAILABLE
 *              (today's AI budget is spent)
 * @param results the places, the closest first; empty unless READY
 */
public record SemanticSearchResponse(String state, List<SemanticHitResponse> results) {
}
