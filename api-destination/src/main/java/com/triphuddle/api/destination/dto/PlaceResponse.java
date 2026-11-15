package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.PlaceKind;
import com.triphuddle.api.destination.entity.PlaceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A place with everything about it.
 *
 * @param id the ID
 * @param slug the slug
 * @param name the name
 * @param kind the kind
 * @param status ACTIVE, HIDDEN or MERGED; only staff see the last two
 * @param countryCode its country, ISO 3166 alpha-2
 * @param ancestors the places it is in, from the top of its country down
 * @param latitude its latitude, or null
 * @param longitude its longitude, or null
 * @param population its population, or null
 * @param isFeatured whether staff feature it
 * @param wikidataId its Wikidata ID, e.g. Q36433, or null
 * @param osmId its OpenStreetMap ID, e.g. relation/295480, or null
 * @param names the names it is known by
 * @param categories its categories
 * @param tags its tags, sorted
 * @param photos its photos, the primary one first
 * @param createdAt when it was added
 * @param updatedAt when it last changed
 */
public record PlaceResponse(
        UUID id,
        String slug,
        String name,
        PlaceKind kind,
        PlaceStatus status,
        String countryCode,
        List<PlaceRef> ancestors,
        Double latitude,
        Double longitude,
        Long population,
        boolean isFeatured,
        String wikidataId,
        String osmId,
        List<PlaceNameResponse> names,
        List<CategoryRef> categories,
        List<String> tags,
        List<PhotoResponse> photos,
        Instant createdAt,
        Instant updatedAt
) {
}
