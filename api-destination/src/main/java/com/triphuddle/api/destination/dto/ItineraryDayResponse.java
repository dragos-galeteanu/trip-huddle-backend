package com.triphuddle.api.destination.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A day of an itinerary.
 *
 * @param id the ID of the day
 * @param dayNumber its number, from 1
 * @param date its date
 * @param title its title
 * @param summary the day in a sentence or two
 * @param isRegenerating whether it is being written again
 * @param items what to do, in order
 */
public record ItineraryDayResponse(
        UUID id,
        int dayNumber,
        LocalDate date,
        String title,
        String summary,
        boolean isRegenerating,
        List<ItineraryItemResponse> items
) {
}
