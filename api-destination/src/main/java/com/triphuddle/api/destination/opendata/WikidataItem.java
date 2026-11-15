package com.triphuddle.api.destination.opendata;

import java.util.Set;

/**
 * A place as Wikidata describes it, with repeated values folded into one.
 *
 * @param id the Wikidata ID, e.g. Q36433
 * @param latitude its latitude, or null
 * @param longitude its longitude, or null
 * @param population its largest stated population, or null
 * @param osmRelationId its OpenStreetMap relation, or null
 * @param imageFileName its Wikimedia Commons image, e.g. {@code Porto_Ribeira.jpg}, or null
 * @param sitelinks how many Wikipedia and sister articles it has
 * @param parentIds the Wikidata IDs of the places the query found it in, among those asked about
 */
public record WikidataItem(
        String id,
        Double latitude,
        Double longitude,
        Long population,
        String osmRelationId,
        String imageFileName,
        int sitelinks,
        Set<String> parentIds
) {
}
