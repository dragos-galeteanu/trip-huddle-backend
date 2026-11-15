package com.triphuddle.api.destination.opendata;

/**
 * A name Wikidata gives an item in a language.
 *
 * @param itemId the Wikidata ID
 * @param languageCode the language, e.g. pt
 * @param label the name
 */
public record WikidataLabel(String itemId, String languageCode, String label) {
}
