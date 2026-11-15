package com.triphuddle.api.destination.content;

import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.Place;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the prompts for a place's content from what is known about it, so the model writes about the right
 * place rather than a namesake.
 */
public final class PlacePrompts {

    private PlacePrompts() {
    }

    /**
     * The system prompt: the voice, the language, and the rules against inventing.
     *
     * @param languageCode the language to write in, ISO 639-1
     * @return the prompt
     */
    public static String system(String languageCode) {
        return """
                You are a careful travel editor writing for a trip-planning app.
                Write in %s, for travellers, in a friendly and plain tone.
                Be specific and factual. Do not invent opening hours, prices, names or events; when unsure, say what
                usually applies or that it varies. Do not mention that you are an AI.
                Answer only with JSON matching the schema.
                """.formatted(languageName(languageCode));
    }

    /**
     * The user prompt: which place, with its facts, and what the section asks for.
     *
     * @param place the place
     * @param ancestors the places it is in, from the top down
     * @param spec the section
     * @return the prompt
     */
    public static String user(Place place, List<Place> ancestors, SectionSpec spec) {
        return facts(place, ancestors) + "\n" + spec.instructions();
    }

    /**
     * What is known about a place, one fact a line, so the model writes about the right one.
     *
     * @param place the place
     * @param ancestors the places it is in, from the top down
     * @return the facts
     */
    public static String facts(Place place, List<Place> ancestors) {
        StringBuilder facts = new StringBuilder();
        facts.append("Place: ").append(place.getName()).append('\n');
        facts.append("Kind: ").append(place.getKind().name().toLowerCase(Locale.ROOT)).append('\n');
        facts.append("Country (ISO 3166): ").append(place.getCountryCode()).append('\n');
        if (!ancestors.isEmpty()) {
            String path = ancestors.stream().map(Place::getName).collect(Collectors.joining(" > "));
            facts.append("Located in: ").append(path).append('\n');
        }
        if (!place.getCategories().isEmpty()) {
            facts.append("Categories: ").append(place.getCategories().stream().map(Category::getName).sorted()
                    .collect(Collectors.joining(", "))).append('\n');
        }
        if (place.getLatitude() != null) {
            facts.append("Coordinates: ").append(place.getLatitude()).append(", ").append(place.getLongitude())
                    .append('\n');
        }
        if (place.getPopulation() != null) {
            facts.append("Population: ").append(place.getPopulation()).append('\n');
        }
        if (place.getWikidataId() != null) {
            facts.append("Wikidata: ").append(place.getWikidataId()).append('\n');
        }
        return facts.toString();
    }

    /**
     * The English name of a language, which models follow best.
     *
     * @param languageCode an ISO 639-1 code
     * @return e.g. "Portuguese", or the code if unknown
     */
    public static String languageName(String languageCode) {
        String name = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH);
        return name.isBlank() ? languageCode : name;
    }
}
