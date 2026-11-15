package com.triphuddle.api.destination.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.destination.trips}; blank or non-positive values fall back to defaults.
 *
 * @param maxTripDays the longest trip an itinerary is written for
 * @param maxPlaces the most destinations one plan may have
 * @param candidatesPerPlace how many places to visit within each destination the AI is offered
 * @param itineraryQuotaEntitlement the license entitlement giving the itineraries a subscriber may have written a
 *                                  month, e.g. {@code AI_ITINERARIES_PER_MONTH: 10}; {@code unlimited} for no limit
 * @param pendingTimeoutMinutes how long an itinerary may take before it is marked failed
 * @param planUrl the plan's page in the frontend, linked from notifications; {@code {planId}} is replaced
 */
@ConfigurationProperties(prefix = "app.destination.trips")
public record TripProperties(
        int maxTripDays,
        int maxPlaces,
        int candidatesPerPlace,
        String itineraryQuotaEntitlement,
        int pendingTimeoutMinutes,
        String planUrl
) {

    public TripProperties {
        maxTripDays = maxTripDays <= 0 ? 30 : maxTripDays;
        maxPlaces = maxPlaces <= 0 ? 10 : maxPlaces;
        candidatesPerPlace = candidatesPerPlace <= 0 ? 25 : candidatesPerPlace;
        itineraryQuotaEntitlement = itineraryQuotaEntitlement == null || itineraryQuotaEntitlement.isBlank()
                ? "AI_ITINERARIES_PER_MONTH" : itineraryQuotaEntitlement;
        pendingTimeoutMinutes = pendingTimeoutMinutes <= 0 ? 30 : pendingTimeoutMinutes;
        planUrl = planUrl == null || planUrl.isBlank() ? "http://localhost:3000/trips/{planId}" : planUrl;
    }
}
