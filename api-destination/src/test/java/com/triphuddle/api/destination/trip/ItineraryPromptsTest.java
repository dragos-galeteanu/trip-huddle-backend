package com.triphuddle.api.destination.trip;

import com.triphuddle.api.destination.content.PlacePrompts;
import com.triphuddle.api.destination.entity.BudgetLevel;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.ItineraryDay;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceKind;
import com.triphuddle.api.destination.entity.TripPace;
import com.triphuddle.api.destination.entity.TripPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the AI is asked when it plans a trip or writes about a place.
 */
class ItineraryPromptsTest {

    private final Place lisbon = place("Lisbon", PlaceKind.CITY);

    @ParameterizedTest(name = "{0} asks for {1} items a day")
    @CsvSource({"RELAXED, 2 to 3", "BALANCED, 3 to 5", "PACKED, 5 to 7"})
    void thePaceSetsHowManyItemsADayHas(TripPace pace, String items) {
        assertThat(ItineraryPrompts.itinerary(plan(pace), List.of(lisbon), List.of(), List.of()))
                .contains(items + " items in order");
    }

    @Test
    void aBarePlanAsksWithoutOfferedPlacesMustSeesInterestsOrNotes() {
        String prompt = ItineraryPrompts.itinerary(plan(TripPace.BALANCED), List.of(lisbon), List.of(), List.of());

        assertThat(prompt).contains("Trip: Lisbon in May", "Dates: 2026-05-04 (Monday) to 2026-05-06",
                "Destinations, in order: Lisbon (PT)", "exactly 3 days")
                .doesNotContain("Offered places", "Include each of these", "Interests:", "Notes from");
    }

    @Test
    void aFullPlanOffersItsPlacesWithCategoriesAndNamesItsMustSeesInterestsAndNotes() {
        TripPlan plan = plan(TripPace.BALANCED);
        plan.setInterests(Set.of("food", "art"));
        plan.setNotes("No museums on Monday");
        Place tower = place("Belem Tower", PlaceKind.POI);
        Category monuments = new Category();
        monuments.setName("Monuments");
        tower.getCategories().add(monuments);

        String prompt = ItineraryPrompts.itinerary(plan, List.of(lisbon), List.of(tower), List.of(tower));

        assertThat(prompt).contains("Interests: art, food", "Notes from the travellers: No museums on Monday",
                "%s | Belem Tower | poi | Monuments".formatted(tower.getId()),
                "Include each of these: Belem Tower (%s)".formatted(tower.getId()));
    }

    @Test
    void aDayIsRewrittenAroundTheOtherDaysAndWhatTheTravellersAsk() {
        TripPlan plan = plan(TripPace.PACKED);
        ItineraryDay first = day(1, "Alfama");
        ItineraryDay second = day(2, "Belem");

        assertThat(ItineraryPrompts.day(plan, List.of(lisbon), List.of(), second, List.of(first), "  More food "))
                .contains("Day 1: Alfama", "Write day 2 again (2026-05-05, Tuesday)",
                        "The travellers ask: More food", "5 to 7 items");
        assertThat(ItineraryPrompts.day(plan, List.of(lisbon), List.of(), second, List.of(first), " "))
                .doesNotContain("The travellers ask");
        assertThat(ItineraryPrompts.system("pt")).contains("Write in Portuguese");
    }

    @Test
    void aPlacesFactsIncludeOnlyWhatIsKnown() {
        Place bare = place("Somewhere", PlaceKind.AREA);
        assertThat(PlacePrompts.facts(bare, List.of())).isEqualTo("""
                Place: Somewhere
                Kind: area
                Country (ISO 3166): PT
                """);

        Category beaches = new Category();
        beaches.setName("Beaches");
        Place known = place("Cascais", PlaceKind.AREA);
        known.getCategories().add(beaches);
        known.setLatitude(38.69);
        known.setLongitude(-9.42);
        known.setPopulation(214_000L);
        known.setWikidataId("Q244521");
        assertThat(PlacePrompts.facts(known, List.of(lisbon))).contains("Located in: Lisbon", "Categories: Beaches",
                "Coordinates: 38.69, -9.42", "Population: 214000", "Wikidata: Q244521");
        assertThat(PlacePrompts.languageName("!!")).as("not a language tag").isEqualTo("!!");
    }

    private static TripPlan plan(TripPace pace) {
        TripPlan plan = new TripPlan();
        plan.setTitle("Lisbon in May");
        plan.setStartDate(LocalDate.parse("2026-05-04"));
        plan.setEndDate(LocalDate.parse("2026-05-06"));
        plan.setAdults(2);
        plan.setBudgetLevel(BudgetLevel.MODERATE);
        plan.setPace(pace);
        return plan;
    }

    private static ItineraryDay day(int number, String title) {
        ItineraryDay day = new ItineraryDay();
        day.setDayNumber(number);
        day.setDate(LocalDate.parse("2026-05-04").plusDays(number - 1L));
        day.setTitle(title);
        return day;
    }

    private static Place place(String name, PlaceKind kind) {
        Place place = new Place();
        place.setId(UUID.randomUUID());
        place.setName(name);
        place.setKind(kind);
        place.setCountryCode("PT");
        return place;
    }
}
