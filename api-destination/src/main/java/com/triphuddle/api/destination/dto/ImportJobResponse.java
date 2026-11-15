package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.ImportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * An import of a country from Wikidata.
 *
 * @param id the ID of the import
 * @param countryCode the country, ISO 3166 alpha-2
 * @param status QUEUED, RUNNING, COMPLETED or FAILED
 * @param stage REGIONS, CITIES, PLACES or PHOTOS while running, else null
 * @param minCityPopulation the smallest population a city needed
 * @param minPoiSitelinks the articles a place to visit needed
 * @param isIncludingPois whether places to visit were included
 * @param isIncludingPhotos whether photos were included
 * @param countryWikidataId the country's Wikidata ID, once found
 * @param placesCreated places added
 * @param placesUpdated places already there and updated
 * @param photosAdded photos added
 * @param error why it FAILED, or null
 * @param requestedByUserId who asked for it
 * @param createdAt when it was asked for
 * @param startedAt when it started, or null
 * @param finishedAt when it ended, or null
 */
public record ImportJobResponse(
        UUID id,
        String countryCode,
        ImportStatus status,
        String stage,
        long minCityPopulation,
        int minPoiSitelinks,
        boolean isIncludingPois,
        boolean isIncludingPhotos,
        String countryWikidataId,
        int placesCreated,
        int placesUpdated,
        int photosAdded,
        String error,
        UUID requestedByUserId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
