package com.triphuddle.api.destination.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the travellers of a trip need to enter its countries.
 *
 * @param planId the plan
 * @param destinationCodes the countries the trip goes to
 * @param isSeeingEveryone whether the caller sees every traveller (a group admin, or staff) or only themselves
 * @param travellers the travellers shown
 * @param rulesUpdatedAt when the rules were last loaded, or null if never
 * @param disclaimer to show with the guidance
 */
public record TripVisaResponse(UUID planId, List<String> destinationCodes, boolean isSeeingEveryone,
                               List<TravellerVisaResponse> travellers, Instant rulesUpdatedAt, String disclaimer) {
}
