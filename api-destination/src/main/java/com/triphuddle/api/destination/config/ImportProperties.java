package com.triphuddle.api.destination.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Settings under {@code app.destination.import}, for importing places from Wikidata and photos from Wikimedia
 * Commons. Blank or non-positive values fall back to defaults.
 *
 * @param sparqlUrl the Wikidata SPARQL endpoint
 * @param commonsApiUrl the Wikimedia Commons API endpoint
 * @param userAgent the User-Agent sent to Wikimedia, which asks for a name and a way to reach the operator
 * @param languages the languages whose names are imported, besides the country's own
 * @param minCityPopulation the default smallest population a city needs
 * @param minPoiSitelinks the default number of Wikipedia articles a place to visit needs, as a measure of notability
 * @param maxPoisPerClass the most places to visit taken per Wikidata class, most notable first
 * @param poiCityRadiusKm how far a place to visit may be from a city to be put in it
 * @param photoWidth the width photos are taken at
 * @param readTimeoutSeconds how long to wait for Wikimedia; SPARQL allows a minute per query
 * @param intervalMs how often waiting imports are looked for
 * @param queryIntervalMs the shortest pause between two Wikidata queries, to stay within its usage policy
 * @param maxAttempts how often a query is tried when Wikidata is rate-limiting or unavailable
 * @param maxRetryWaitSeconds the longest wait before a retry, also the cap on what Retry-After may ask for
 */
@ConfigurationProperties(prefix = "app.destination.import")
public record ImportProperties(
        String sparqlUrl,
        String commonsApiUrl,
        String userAgent,
        List<String> languages,
        long minCityPopulation,
        int minPoiSitelinks,
        int maxPoisPerClass,
        double poiCityRadiusKm,
        int photoWidth,
        int readTimeoutSeconds,
        long intervalMs,
        long queryIntervalMs,
        int maxAttempts,
        int maxRetryWaitSeconds
) {

    public ImportProperties {
        sparqlUrl = isBlank(sparqlUrl) ? "https://query.wikidata.org/sparql" : sparqlUrl;
        commonsApiUrl = isBlank(commonsApiUrl) ? "https://commons.wikimedia.org/w/api.php" : commonsApiUrl;
        userAgent = isBlank(userAgent) ? "TripHuddleBot/0.1 (set app.destination.import.user-agent)" : userAgent;
        languages = languages == null || languages.isEmpty() ? List.of("en") : List.copyOf(languages);
        minCityPopulation = minCityPopulation <= 0 ? 20_000 : minCityPopulation;
        minPoiSitelinks = minPoiSitelinks <= 0 ? 5 : minPoiSitelinks;
        maxPoisPerClass = maxPoisPerClass <= 0 ? 300 : maxPoisPerClass;
        poiCityRadiusKm = poiCityRadiusKm <= 0 ? 25 : poiCityRadiusKm;
        photoWidth = photoWidth <= 0 ? 1600 : photoWidth;
        readTimeoutSeconds = readTimeoutSeconds <= 0 ? 90 : readTimeoutSeconds;
        intervalMs = intervalMs <= 0 ? 30_000 : intervalMs;
        queryIntervalMs = queryIntervalMs <= 0 ? 2_000 : queryIntervalMs;
        maxAttempts = maxAttempts <= 0 ? 4 : maxAttempts;
        maxRetryWaitSeconds = maxRetryWaitSeconds <= 0 ? 120 : maxRetryWaitSeconds;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
