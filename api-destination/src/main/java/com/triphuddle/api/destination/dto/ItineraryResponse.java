package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.ItineraryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An itinerary of a trip plan.
 *
 * @param id the ID of the itinerary
 * @param planId its plan
 * @param status GENERATING (ask again shortly), READY or FAILED
 * @param languageCode the language it is written in
 * @param summary the trip in a few sentences, or null until ready
 * @param days its days, in order
 * @param model the model that wrote it
 * @param error why it FAILED, or null
 * @param requestedByUserId who asked for it
 * @param createdAt when it was asked for
 * @param generatedAt when it was written, or null
 * @param isAiGenerated always true, for the label the frontend must show
 */
public record ItineraryResponse(
        UUID id,
        UUID planId,
        ItineraryStatus status,
        String languageCode,
        String summary,
        List<ItineraryDayResponse> days,
        String model,
        String error,
        UUID requestedByUserId,
        Instant createdAt,
        Instant generatedAt,
        boolean isAiGenerated
) {
}
