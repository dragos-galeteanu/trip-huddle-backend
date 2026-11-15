package com.triphuddle.api.destination.dto;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.entity.BudgetLevel;
import com.triphuddle.api.destination.entity.TripPace;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A trip plan.
 *
 * @param id the ID of the plan
 * @param ownerType USER or GROUP
 * @param ownerId the user or group
 * @param createdByUserId who created it
 * @param title its title
 * @param startDate the first day
 * @param endDate the last day
 * @param dayCount how many days, both ends included
 * @param adults adult travellers
 * @param children child travellers
 * @param budgetLevel BUDGET, MODERATE or LUXURY
 * @param pace RELAXED, BALANCED or PACKED
 * @param interests what the travellers like
 * @param places the destinations, in order
 * @param mustSees places to include whatever happens
 * @param notes anything else the travellers said
 * @param languageCode the language itineraries are written in
 * @param createdAt when it was created
 * @param updatedAt when it last changed
 */
public record TripPlanResponse(
        UUID id,
        LicenseTarget ownerType,
        UUID ownerId,
        UUID createdByUserId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int dayCount,
        int adults,
        int children,
        BudgetLevel budgetLevel,
        TripPace pace,
        List<String> interests,
        List<PlaceRef> places,
        List<PlaceRef> mustSees,
        String notes,
        String languageCode,
        Instant createdAt,
        Instant updatedAt
) {
}
