package com.triphuddle.api.destination.dto;

/**
 * A name a place is known by.
 *
 * @param languageCode an ISO 639-1 code, or {@code und}
 * @param name the name
 * @param isAlias whether it is an alias rather than the name in that language
 */
public record PlaceNameResponse(
        String languageCode,
        String name,
        boolean isAlias
) {
}
