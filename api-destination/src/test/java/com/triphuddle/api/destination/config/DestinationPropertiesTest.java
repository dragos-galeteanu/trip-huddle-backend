package com.triphuddle.api.destination.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settings left out fall back to production's defaults, and settings given are kept.
 */
class DestinationPropertiesTest {

    @Test
    void importSettingsLeftOutGetTheirDefaults() {
        ImportProperties defaults = new ImportProperties(" ", null, "", List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(defaults.sparqlUrl()).isEqualTo("https://query.wikidata.org/sparql");
        assertThat(defaults.commonsApiUrl()).isEqualTo("https://commons.wikimedia.org/w/api.php");
        assertThat(defaults.userAgent()).startsWith("TripHuddleBot/");
        assertThat(defaults.languages()).containsExactly("en");
        assertThat(defaults.minCityPopulation()).isEqualTo(20_000);
        assertThat(defaults.minPoiSitelinks()).isEqualTo(5);
        assertThat(defaults.maxPoisPerClass()).isEqualTo(300);
        assertThat(defaults.poiCityRadiusKm()).isEqualTo(25);
        assertThat(defaults.photoWidth()).isEqualTo(1600);
        assertThat(defaults.readTimeoutSeconds()).isEqualTo(90);
        assertThat(defaults.intervalMs()).isEqualTo(30_000);
        assertThat(defaults.queryIntervalMs()).isEqualTo(2_000);
        assertThat(defaults.maxAttempts()).isEqualTo(4);
        assertThat(defaults.maxRetryWaitSeconds()).isEqualTo(120);
        assertThat(new ImportProperties(null, null, null, null, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1).languages())
                .containsExactly("en");
    }

    @Test
    void importSettingsGivenAreKept() {
        ImportProperties set = new ImportProperties("https://sparql.example", "https://commons.example", "Bot/1",
                List.of("pt", "en"), 5_000, 2, 10, 5.5, 800, 30, 1_000, 500, 2, 60);

        assertThat(set).isEqualTo(new ImportProperties("https://sparql.example", "https://commons.example", "Bot/1",
                List.of("pt", "en"), 5_000, 2, 10, 5.5, 800, 30, 1_000, 500, 2, 60));
        assertThat(set.languages()).containsExactly("pt", "en");
        assertThat(set.poiCityRadiusKm()).isEqualTo(5.5);
        assertThat(set.maxRetryWaitSeconds()).isEqualTo(60);
    }

    @Test
    void contentSettingsLeftOutGetTheirDefaultsAndTheHardLimitIsNeverBelowTheBudget() {
        ContentProperties defaults = new ContentProperties(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(defaults).isEqualTo(new ContentProperties(180, 30, 90, 30, 2_000_000, 5_000_000, 30, 60, 30,
                600_000, 50));
        assertThat(new ContentProperties(1, 1, 1, 1, 1_000, 10, 1, 1, 1, 1, 1).dailyTokenHardLimit())
                .isEqualTo(1_000);
        assertThat(new ContentProperties(1, 1, 1, 1, 1_000, 3_000, 1, 1, 1, 1, 1).dailyTokenHardLimit())
                .isEqualTo(3_000);
    }

    @Test
    void tripAndDestinationSettingsLeftOutGetTheirDefaults() {
        assertThat(new TripProperties(0, 0, 0, " ", 0, null)).isEqualTo(new TripProperties(30, 10, 25,
                "AI_ITINERARIES_PER_MONTH", 30, "http://localhost:3000/trips/{planId}"));
        assertThat(new TripProperties(5, 3, 7, "PLANS", 15, "https://app.example/{planId}").planUrl())
                .isEqualTo("https://app.example/{planId}");

        DestinationProperties defaults = new DestinationProperties(null, -1, 0, null);
        assertThat(defaults.accessEntitlement()).isEqualTo("DESTINATIONS");
        assertThat(defaults.accessGraceHours()).isEqualTo(48);
        assertThat(defaults.maxRadiusKm()).isEqualTo(500);
        assertThat(defaults.photos().isEnabled()).isFalse();
        DestinationProperties set = new DestinationProperties(" ACCESS ", 0, 100, null);
        assertThat(set.accessEntitlement()).isEqualTo("ACCESS");
        assertThat(set.accessGraceHours()).as("no grace is a valid choice").isZero();
        assertThat(set.maxRadiusKm()).isEqualTo(100);
    }
}
