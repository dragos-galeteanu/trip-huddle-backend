package com.triphuddle.api.destination.opendata;

import java.util.List;

/**
 * A country in Wikidata.
 *
 * @param id its Wikidata ID, e.g. Q45
 * @param languages the ISO 639-1 codes of its official languages
 */
public record WikidataCountry(String id, List<String> languages) {
}
