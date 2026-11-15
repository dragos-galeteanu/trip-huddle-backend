package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.TimeOfDay;

import java.util.UUID;

/**
 * One thing to do on an itinerary's day.
 *
 * @param id the ID of the item
 * @param position its place in the day, from 0
 * @param timeOfDay MORNING, AFTERNOON or EVENING
 * @param title its title
 * @param description what it is
 * @param place the place it is about, or null
 * @param durationMinutes roughly how long, or null
 * @param tips tips, or null
 */
public record ItineraryItemResponse(
        UUID id,
        int position,
        TimeOfDay timeOfDay,
        String title,
        String description,
        PlaceRef place,
        Integer durationMinutes,
        String tips
) {
}
