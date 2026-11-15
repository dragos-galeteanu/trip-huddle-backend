package com.triphuddle.api.destination.trip;

import com.triphuddle.api.destination.content.PlacePrompts;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.ItineraryDay;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.TripPlan;

import java.time.format.TextStyle;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the prompts for an itinerary and for rewriting one day, offering the places we know so that items link
 * to them.
 */
final class ItineraryPrompts {

    private ItineraryPrompts() {
    }

    static String system(String languageCode) {
        return """
                You are an experienced trip planner writing itineraries for a trip-planning app.
                Write in %s. Plan realistic days: travel time between places, meals, rest, and the pace asked for.
                Prefer the offered places, and give an item's placeId only from the offered list, else "".
                Opening days and hours vary: do not state them as fact; suggest checking where it matters.
                Do not invent prices. Do not mention that you are an AI. Answer only with JSON matching the schema.
                """.formatted(PlacePrompts.languageName(languageCode));
    }

    static String itinerary(TripPlan plan, List<Place> destinations, Collection<Place> candidates,
                            Collection<Place> mustSees) {
        return trip(plan, destinations) + offered(candidates) + mustSee(mustSees) + """

                Write an itinerary of exactly %d days, dayNumber 1 to %d, day 1 being %s.
                Each day: a title, a one or two sentence summary, and %s items in order, each with a time of day,
                a title, a description, a placeId, a duration in minutes and tips (or "").
                Then a summary of the whole trip in 2 to 4 sentences.
                """.formatted(plan.dayCount(), plan.dayCount(), plan.getStartDate(), itemsPerDay(plan));
    }

    static String day(TripPlan plan, List<Place> destinations, Collection<Place> candidates, ItineraryDay day,
                      List<ItineraryDay> otherDays, String instructions) {
        String others = otherDays.stream().map(other -> "Day %d: %s".formatted(other.getDayNumber(), other.getTitle()))
                .collect(Collectors.joining("\n"));
        return trip(plan, destinations) + offered(candidates) + """

                The other days are:
                %s

                Write day %d again (%s), not repeating what the other days do. %s
                Give a title, a one or two sentence summary, and %s items in order.
                """.formatted(others, day.getDayNumber(), weekday(day), instructions == null || instructions.isBlank()
                ? "" : "The travellers ask: " + instructions.strip(), itemsPerDay(plan));
    }

    private static String trip(TripPlan plan, List<Place> destinations) {
        StringBuilder text = new StringBuilder();
        text.append("Trip: ").append(plan.getTitle()).append('\n');
        text.append("Dates: ").append(plan.getStartDate()).append(" (")
                .append(plan.getStartDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .append(") to ").append(plan.getEndDate()).append('\n');
        text.append("Travellers: ").append(plan.getAdults()).append(" adults, ").append(plan.getChildren())
                .append(" children\n");
        text.append("Budget: ").append(plan.getBudgetLevel().name().toLowerCase(Locale.ROOT)).append('\n');
        text.append("Pace: ").append(plan.getPace().name().toLowerCase(Locale.ROOT)).append('\n');
        if (!plan.getInterests().isEmpty()) {
            text.append("Interests: ").append(plan.getInterests().stream().sorted().collect(Collectors.joining(", ")))
                    .append('\n');
        }
        text.append("Destinations, in order: ").append(destinations.stream()
                .map(place -> place.getName() + " (" + place.getCountryCode() + ")")
                .collect(Collectors.joining(", "))).append('\n');
        if (plan.getNotes() != null) {
            text.append("Notes from the travellers: ").append(plan.getNotes()).append('\n');
        }
        return text.toString();
    }

    private static String offered(Collection<Place> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        return "\nOffered places (placeId | name | kind | categories):\n" + candidates.stream()
                .map(place -> "%s | %s | %s | %s".formatted(place.getId(), place.getName(),
                        place.getKind().name().toLowerCase(Locale.ROOT), place.getCategories().stream()
                                .map(Category::getName).sorted().collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n")) + "\n";
    }

    private static String mustSee(Collection<Place> mustSees) {
        if (mustSees.isEmpty()) {
            return "";
        }
        return "\nInclude each of these: " + mustSees.stream().map(place -> place.getName() + " (" + place.getId()
                + ")").collect(Collectors.joining(", ")) + "\n";
    }

    private static String itemsPerDay(TripPlan plan) {
        return switch (plan.getPace()) {
            case RELAXED -> "2 to 3";
            case BALANCED -> "3 to 5";
            case PACKED -> "5 to 7";
        };
    }

    private static String weekday(ItineraryDay day) {
        return day.getDate() + ", " + day.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
