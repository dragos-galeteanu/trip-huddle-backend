package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.PlaceKind;
import java.util.List;
import java.util.UUID;

/**
 * A place in a list or search result.
 *
 * @param id the ID
 * @param slug the slug
 * @param name the name
 * @param kind the kind
 * @param countryCode its country, ISO 3166 alpha-2
 * @param parent the place it is in, or null at the top of its country
 * @param latitude its latitude, or null
 * @param longitude its longitude, or null
 * @param population its population, or null
 * @param isFeatured whether staff feature it
 * @param categories its categories
 * @param distanceKm how far it is from the searched point, or null without one
 * @param photo its primary photo, or null
 */
public record PlaceSummaryResponse(
        UUID id,
        String slug,
        String name,
        PlaceKind kind,
        String countryCode,
        PlaceRef parent,
        Double latitude,
        Double longitude,
        Long population,
        boolean isFeatured,
        List<CategoryRef> categories,
        Double distanceKm,
        PhotoResponse photo
) {
}
