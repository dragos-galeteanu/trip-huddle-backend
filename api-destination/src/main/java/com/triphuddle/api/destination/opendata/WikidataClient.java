package com.triphuddle.api.destination.opendata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.api.destination.config.ImportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads countries, their regions, cities and places to visit, and their names, from Wikidata's SPARQL endpoint.
 */
@Slf4j
public class WikidataClient {

    // Wikidata's classes: first-level administrative subdivision of a country, and human settlement.
    static final String REGION_CLASS = "Q10864048";
    static final String SETTLEMENT_CLASS = "Q486972";
    private static final Pattern ITEM_ID = Pattern.compile("^Q[0-9]+$");
    private static final Pattern POINT = Pattern.compile("Point\\(([-0-9.eE]+) ([-0-9.eE]+)\\)");
    private static final int LABEL_BATCH = 200;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ImportProperties properties;
    private final Sleeper sleeper;
    private final Object pacing = new Object();
    private long lastQueryAtMs;

    /**
     * Pauses between queries and before retries; a parameter so tests need not wait.
     */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /**
     * @param restClient the HTTP client, pointed at the SPARQL endpoint and sending Wikimedia's User-Agent
     * @param objectMapper the JSON mapper
     * @param properties the import settings: pacing and retries
     * @param sleeper how to pause
     */
    public WikidataClient(RestClient restClient, ObjectMapper objectMapper, ImportProperties properties,
                          Sleeper sleeper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * Find a country by its ISO code, and the languages it speaks.
     *
     * @param alpha2 the ISO 3166 alpha-2 code
     * @return its Wikidata ID and the language codes of its official languages, or empty if Wikidata has none
     */
    public Optional<WikidataCountry> findCountry(String alpha2) {
        if (alpha2 == null || !alpha2.matches("^[A-Za-z]{2}$")) {
            throw new IllegalArgumentException("Not an ISO 3166 alpha-2 code: " + alpha2);
        }
        // A code may sit on historical states too; the one with the most articles is the country of today.
        List<Map<String, String>> rows = select("""
                SELECT ?country ?links ?language WHERE {
                  ?country wdt:P297 "%s" ; wikibase:sitelinks ?links .
                  OPTIONAL { ?country wdt:P37/wdt:P424 ?language . }
                } ORDER BY DESC(?links)
                """.formatted(alpha2.toUpperCase()));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String countryId = itemIdOf(rows.get(0).get("country"));
        Set<String> languages = rows.stream()
                .filter(row -> countryId.equals(itemIdOf(row.get("country"))) && row.get("language") != null)
                .map(row -> row.get("language").toLowerCase())
                .filter(code -> code.matches("^[a-z]{2}$"))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return Optional.of(new WikidataCountry(countryId, List.copyOf(languages)));
    }

    /**
     * Get a country's first-level regions that still exist.
     *
     * @param countryId the country's Wikidata ID
     * @return the regions
     */
    public List<WikidataItem> regions(String countryId) {
        return items(select("""
                SELECT ?item ?coord ?population ?osm ?image ?links WHERE {
                  ?item wdt:P31/wdt:P279* wd:%s ; wdt:P17 wd:%s ; wikibase:sitelinks ?links .
                  FILTER NOT EXISTS { ?item wdt:P576 ?dissolved . }
                  OPTIONAL { ?item wdt:P625 ?coord . }
                  OPTIONAL { ?item wdt:P1082 ?population . }
                  OPTIONAL { ?item wdt:P402 ?osm . }
                  OPTIONAL { ?item wdt:P18 ?image . }
                }
                """.formatted(REGION_CLASS, requireItemId(countryId))));
    }

    /**
     * Get a country's settlements with at least a population, and which of the given regions each lies in.
     *
     * @param countryId the country's Wikidata ID
     * @param minPopulation the smallest population
     * @param regionIds the regions to place them in
     * @return the cities
     */
    public List<WikidataItem> cities(String countryId, long minPopulation, Collection<String> regionIds) {
        String regions = regionIds.isEmpty() ? "" : """
                  OPTIONAL { ?item wdt:P131+ ?parent . VALUES ?parent { %s } }
                """.formatted(values(regionIds));
        return items(select("""
                SELECT ?item ?coord ?population ?osm ?image ?links ?parent WHERE {
                  ?item wdt:P31/wdt:P279* wd:%s ; wdt:P17 wd:%s ; wdt:P1082 ?population ;
                        wikibase:sitelinks ?links .
                  FILTER(?population >= %d)
                  FILTER NOT EXISTS { ?item wdt:P576 ?dissolved . }
                  OPTIONAL { ?item wdt:P625 ?coord . }
                  OPTIONAL { ?item wdt:P402 ?osm . }
                  OPTIONAL { ?item wdt:P18 ?image . }
                %s}
                """.formatted(SETTLEMENT_CLASS, requireItemId(countryId), minPopulation, regions)));
    }

    /**
     * Get a country's most notable items of a class, e.g. castles, that have coordinates.
     *
     * @param countryId the country's Wikidata ID
     * @param classId the class, e.g. Q23413 for castle; its subclasses count too
     * @param minSitelinks the fewest articles an item needs
     * @param limit the most items, most notable first
     * @return the items
     */
    public List<WikidataItem> placesOfClass(String countryId, String classId, int minSitelinks, int limit) {
        return items(select("""
                SELECT ?item ?coord ?osm ?image ?links WHERE {
                  {
                    SELECT DISTINCT ?item ?links WHERE {
                      ?item wdt:P31/wdt:P279* wd:%s ; wdt:P17 wd:%s ; wdt:P625 ?anyCoord ;
                            wikibase:sitelinks ?links .
                      FILTER(?links >= %d)
                    } ORDER BY DESC(?links) LIMIT %d
                  }
                  OPTIONAL { ?item wdt:P625 ?coord . }
                  OPTIONAL { ?item wdt:P402 ?osm . }
                  OPTIONAL { ?item wdt:P18 ?image . }
                }
                """.formatted(requireItemId(classId), requireItemId(countryId), minSitelinks, limit)));
    }

    /**
     * Get the names of items in some languages.
     *
     * @param itemIds the Wikidata IDs
     * @param languages the language codes
     * @return their names, in no order
     */
    public List<WikidataLabel> labels(Collection<String> itemIds, Collection<String> languages) {
        List<String> ids = new ArrayList<>(new HashSet<>(itemIds));
        String langs = languages.stream().map(code -> "\"" + code.replaceAll("[^a-z-]", "") + "\"")
                .collect(Collectors.joining(", "));
        List<WikidataLabel> labels = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += LABEL_BATCH) {
            List<String> batch = ids.subList(from, Math.min(from + LABEL_BATCH, ids.size()));
            for (Map<String, String> row : select("""
                    SELECT ?item ?label WHERE {
                      VALUES ?item { %s }
                      ?item rdfs:label ?label .
                      FILTER(LANG(?label) IN (%s))
                    }
                    """.formatted(values(batch), langs))) {
                labels.add(new WikidataLabel(itemIdOf(row.get("item")), row.get("label@lang"), row.get("label")));
            }
        }
        return labels;
    }

    // Folds the rows of each item into one: first coordinate, OSM ID and image, largest population, every parent.
    static List<WikidataItem> items(List<Map<String, String>> rows) {
        Map<String, WikidataItem> byId = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String id = itemIdOf(row.get("item"));
            WikidataItem seen = byId.get(id);
            Double[] point = pointOf(row.get("coord"));
            Long population = longOf(row.get("population"));
            String parent = row.get("parent") == null ? null : itemIdOf(row.get("parent"));
            Set<String> parents = seen == null ? new HashSet<>() : new HashSet<>(seen.parentIds());
            if (parent != null) {
                parents.add(parent);
            }
            byId.put(id, new WikidataItem(id,
                    seen != null && seen.latitude() != null ? seen.latitude() : point[0],
                    seen != null && seen.longitude() != null ? seen.longitude() : point[1],
                    max(seen == null ? null : seen.population(), population),
                    seen != null && seen.osmRelationId() != null ? seen.osmRelationId() : row.get("osm"),
                    seen != null && seen.imageFileName() != null ? seen.imageFileName() : fileNameOf(row.get("image")),
                    Math.max(seen == null ? 0 : seen.sitelinks(), intOf(row.get("links"))),
                    Set.copyOf(parents)));
        }
        return new ArrayList<>(byId.values());
    }

    // One query at a time per instance, spaced out, and retried while Wikidata is rate-limiting or unavailable.
    private List<Map<String, String>> select(String query) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", query);
        synchronized (pacing) {
            for (int attempt = 1; ; attempt++) {
                pause(Duration.ofMillis(Math.max(0,
                        lastQueryAtMs + properties.queryIntervalMs() - System.currentTimeMillis())));
                lastQueryAtMs = System.currentTimeMillis();
                Answer answer = post(form);
                if (answer.body() != null) {
                    try {
                        return bindingsOf(objectMapper.readTree(answer.body()));
                    } catch (IOException e) {
                        throw new OpenDataException("Wikidata answered something that isn't JSON", e);
                    }
                }
                if (!answer.isRetryable() || attempt >= properties.maxAttempts()) {
                    throw new OpenDataException("Wikidata query failed after %d attempts: HTTP %d"
                            .formatted(attempt, answer.status()));
                }
                long backoff = Math.min(5L << (attempt - 1), properties.maxRetryWaitSeconds());
                long wait = answer.retryAfterSeconds() == null ? backoff
                        : Math.min(Math.max(answer.retryAfterSeconds(), 1), properties.maxRetryWaitSeconds());
                log.warn("Wikidata is limiting or unavailable, retrying [status={}, attempt={}, waitSeconds={}]",
                        answer.status(), attempt, wait);
                pause(Duration.ofSeconds(wait));
            }
        }
    }

    private record Answer(int status, String body, Long retryAfterSeconds) {
        boolean isRetryable() {
            return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }
    }

    private Answer post(MultiValueMap<String, String> form) {
        try {
            return restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT, "application/sparql-results+json")
                    .body(form)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return new Answer(status, raw, null);
                        }
                        return new Answer(status, null, retryAfterOf(response.getHeaders().getFirst("Retry-After")));
                    });
        } catch (RestClientException e) {
            // A timeout: Wikidata stops queries at a minute, so it may pass on a quieter try.
            return new Answer(504, null, null);
        }
    }

    private static Long retryAfterOf(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenDataException("Interrupted while waiting for Wikidata", e);
        }
    }

    // Each binding's value by variable name; a label's language goes in as "<name>@lang".
    private static List<Map<String, String>> bindingsOf(JsonNode root) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode binding : root.path("results").path("bindings")) {
            Map<String, String> row = new LinkedHashMap<>();
            binding.fields().forEachRemaining(field -> {
                row.put(field.getKey(), field.getValue().path("value").asText());
                if (field.getValue().has("xml:lang")) {
                    row.put(field.getKey() + "@lang", field.getValue().path("xml:lang").asText());
                }
            });
            rows.add(row);
        }
        return rows;
    }

    static String itemIdOf(String uri) {
        return uri == null ? null : uri.substring(uri.lastIndexOf('/') + 1);
    }

    // A Commons file URL ends with its URL-encoded name.
    static String fileNameOf(String uri) {
        if (uri == null) {
            return null;
        }
        String encoded = uri.substring(uri.lastIndexOf('/') + 1);
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8).replace(' ', '_');
    }

    private static Double[] pointOf(String wkt) {
        if (wkt == null) {
            return new Double[] {null, null};
        }
        Matcher matcher = POINT.matcher(wkt);
        if (!matcher.find()) {
            return new Double[] {null, null};
        }
        // Wikidata writes longitude first.
        return new Double[] {Double.parseDouble(matcher.group(2)), Double.parseDouble(matcher.group(1))};
    }

    private static Long longOf(String value) {
        try {
            return value == null ? null : (long) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOf(String value) {
        Long parsed = longOf(value);
        return parsed == null ? 0 : parsed.intValue();
    }

    private static Long max(Long a, Long b) {
        // Not one ternary: with Math.max's long in it, Java would unbox a null b and throw.
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    private static String values(Collection<String> itemIds) {
        return itemIds.stream().map(WikidataClient::requireItemId).map(id -> "wd:" + id)
                .collect(Collectors.joining(" "));
    }

    // Only Q-numbers go into a query, so nothing else can be injected into the SPARQL.
    private static String requireItemId(String id) {
        if (id == null || !ITEM_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Not a Wikidata item ID: " + id);
        }
        return id;
    }
}
