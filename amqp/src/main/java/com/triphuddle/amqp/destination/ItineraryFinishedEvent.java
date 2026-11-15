package com.triphuddle.amqp.destination;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published when an itinerary was written, or failed to be, for the trip's travellers to hear of it.
 *
 * @param itineraryId the ID of the itinerary
 * @param planId the ID of its trip plan
 * @param planTitle the plan's title
 * @param status READY or FAILED
 * @param travellerUserIds everyone who shares the plan: its user, or its group's members
 * @param requestedByUserId who asked for it
 * @param startDate the trip's first day
 * @param dayCount how many days it has
 * @param planUrl the plan's page in the frontend
 */
public record ItineraryFinishedEvent(
        UUID itineraryId,
        UUID planId,
        String planTitle,
        String status,
        List<UUID> travellerUserIds,
        UUID requestedByUserId,
        LocalDate startDate,
        int dayCount,
        String planUrl
) {
}
