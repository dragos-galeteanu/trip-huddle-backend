package com.triphuddle.api.destination.trip;

import java.util.List;

/**
 * The AI's itinerary, as its schema shapes it.
 *
 * @param summary the trip in a few sentences
 * @param days the days
 */
record ItineraryAnswer(String summary, List<Day> days) {

    /**
     * One day.
     *
     * @param dayNumber its number, from 1
     * @param title its title
     * @param summary the day in a sentence or two
     * @param items what to do, in order
     */
    record Day(int dayNumber, String title, String summary, List<Item> items) {
    }

    /**
     * One thing to do.
     *
     * @param timeOfDay MORNING, AFTERNOON or EVENING
     * @param title its title
     * @param description what it is
     * @param placeId the ID of an offered place, or empty for anything else
     * @param durationMinutes roughly how long
     * @param tips tips, or empty
     */
    record Item(String timeOfDay, String title, String description, String placeId, int durationMinutes,
                String tips) {
    }
}
