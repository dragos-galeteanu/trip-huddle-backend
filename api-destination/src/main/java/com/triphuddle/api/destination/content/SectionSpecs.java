package com.triphuddle.api.destination.content;

import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.entity.ContentSection;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * The spec of each section. Schemas are strict, as the openai worker asks for them: every property required, no
 * others allowed, so the frontend can rely on the shape.
 */
@Component
public class SectionSpecs {

    private static final String STRINGS = "{\"type\":\"array\",\"items\":{\"type\":\"string\"}}";

    private final Map<ContentSection, SectionSpec> specs = new EnumMap<>(ContentSection.class);

    /**
     * @param properties how long each section stays fresh
     */
    public SectionSpecs(ContentProperties properties) {
        specs.put(ContentSection.OVERVIEW, new SectionSpec(ContentSection.OVERVIEW, "place-overview", "place_overview",
                object("summary", "{\"type\":\"string\"}", "highlights", STRINGS, "pros", STRINGS, "cons", STRINGS,
                        "goodFor", STRINGS, "notIdealFor", STRINGS),
                false, Duration.ofDays(properties.overviewFreshDays()), """
                Write a traveller's overview of the place:
                - summary: 2 to 4 sentences on what it is and why people go.
                - highlights: 3 to 6 things not to miss.
                - pros and cons: 3 to 5 each, specific to this place rather than to travel in general.
                - goodFor and notIdealFor: kinds of travellers, e.g. families, couples, budget travellers,
                  wheelchair users, people who dislike crowds.
                """));
        specs.put(ContentSection.OPINIONS, new SectionSpec(ContentSection.OPINIONS, "place-opinions", "place_opinions",
                object("summary", "{\"type\":\"string\"}", "praise", STRINGS, "complaints", STRINGS,
                        "sentiment", "{\"type\":\"string\",\"enum\":[\"positive\",\"mixed\",\"negative\"]}"),
                true, Duration.ofDays(properties.opinionsFreshDays()), """
                Search the web for what visitors say about the place: reviews, travel forums and travel guides.
                - summary: 2 to 4 sentences on the general opinion.
                - praise and complaints: 3 to 6 each, the points that come up most often, in plain words.
                - sentiment: positive, mixed or negative overall.
                Report only what the sources say; if they say little, keep the lists short rather than invent.
                """));
        specs.put(ContentSection.PRACTICAL, new SectionSpec(ContentSection.PRACTICAL, "place-practical",
                "place_practical",
                object("bestTimeToVisit", "{\"type\":\"string\"}", "suggestedDuration", "{\"type\":\"string\"}",
                        "costs", "{\"type\":\"string\"}", "gettingThere", "{\"type\":\"string\"}",
                        "gettingAround", "{\"type\":\"string\"}", "safety", "{\"type\":\"string\"}",
                        "customs", "{\"type\":\"string\"}", "tips", STRINGS),
                false, Duration.ofDays(properties.practicalFreshDays()), """
                Write practical information for visiting the place, each field 1 to 3 sentences:
                bestTimeToVisit, suggestedDuration, costs (rough, and in the local currency), gettingThere,
                gettingAround, safety, customs; and tips: 3 to 6 short tips. Say when something varies or may have
                changed, rather than state uncertain details as fact.
                """));
    }

    /**
     * Get a section's spec.
     *
     * @param section the section
     * @return its spec
     */
    public SectionSpec of(ContentSection section) {
        return specs.get(section);
    }

    // A strict object schema: the given properties, all required, nothing else.
    private static String object(String... namesAndSchemas) {
        StringBuilder properties = new StringBuilder();
        StringBuilder required = new StringBuilder();
        for (int i = 0; i < namesAndSchemas.length; i += 2) {
            if (i > 0) {
                properties.append(',');
                required.append(',');
            }
            properties.append('"').append(namesAndSchemas[i]).append("\":").append(namesAndSchemas[i + 1]);
            required.append('"').append(namesAndSchemas[i]).append('"');
        }
        return "{\"type\":\"object\",\"properties\":{%s},\"required\":[%s],\"additionalProperties\":false}"
                .formatted(properties, required);
    }
}
