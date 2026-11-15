package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.PlaceKind;

import java.util.UUID;

/**
 * What to search for; paths are resolved by the caller, every field is optional.
 *
 * @param text words to match against every name, or null
 * @param countryCode a country, ISO 3166 alpha-2, or null
 * @param kind a kind, or null
 * @param categoryPath the path of a category whose subtree matches, or null
 * @param tag a tag, or null
 * @param withinId a place whose subtree is searched, without itself, or null
 * @param withinPath that place's path, or null
 * @param parentId a place whose direct children match, or null
 * @param isFeatured featured or not, or null for both
 * @param latitude the centre of a radius search, or null
 * @param longitude the centre of a radius search, or null
 * @param radiusMeters the radius, or null
 * @param west the west edge of a map area, or null
 * @param south the south edge
 * @param east the east edge
 * @param north the north edge
 * @param isIncludingHidden whether hidden places match too; merged ones never do
 */
public record PlaceSearchQuery(
        String text,
        String countryCode,
        PlaceKind kind,
        String categoryPath,
        String tag,
        UUID withinId,
        String withinPath,
        UUID parentId,
        Boolean isFeatured,
        Double latitude,
        Double longitude,
        Double radiusMeters,
        Double west,
        Double south,
        Double east,
        Double north,
        boolean isIncludingHidden
) {

    /**
     * Check if a point is given to measure distances from.
     *
     * @return true with a latitude and longitude
     */
    public boolean hasPoint() {
        return latitude != null && longitude != null;
    }

    /**
     * Check if a whole map area is given.
     *
     * @return true with all four edges
     */
    public boolean hasArea() {
        return west != null && south != null && east != null && north != null;
    }
}
