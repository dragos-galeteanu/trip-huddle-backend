package com.triphuddle.api.destination.opendata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.api.destination.config.ImportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pins the SPARQL answers' parsing, the pacing and the retries against mocked HTTP. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WikidataClientTest {

    static final String ENTITY = "http://www.wikidata.org/entity/";

    final List<Duration> sleeps = new ArrayList<>();
    MockRestServiceServer server;
    WikidataClient client;

    static ImportProperties properties(int maxAttempts) {
        return new ImportProperties("https://query.example/sparql", "https://commons.example/w/api.php", "Test/1",
                List.of("en"), 20_000, 5, 300, 25, 1600, 90, 30_000, 0, maxAttempts, 120);
    }

    @BeforeEach
    void client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://query.example/sparql");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WikidataClient(builder.build(), new ObjectMapper(), properties(3), sleeps::add);
    }

    static String bindings(String... rows) {
        return "{\"results\": {\"bindings\": [" + String.join(",", rows) + "]}}";
    }

    static String uri(String name, String value) {
        return "\"%s\": {\"type\": \"uri\", \"value\": \"%s\"}".formatted(name, value);
    }

    static String literal(String name, String value) {
        return "\"%s\": {\"type\": \"literal\", \"value\": \"%s\"}".formatted(name, value);
    }

    void answers(String json) {
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void a_country_is_the_one_with_the_most_articles_and_its_languages_are_two_letter_codes() {
        answers(bindings(
                "{" + uri("country", ENTITY + "Q45") + "," + literal("links", "300") + "," + literal("language", "PT")
                        + "}",
                "{" + uri("country", ENTITY + "Q45") + "," + literal("links", "300") + "," + literal("language", "mwl")
                        + "}",
                "{" + uri("country", ENTITY + "Q1") + "," + literal("links", "10") + "," + literal("language", "la")
                        + "}"));

        WikidataCountry country = client.findCountry("pt").orElseThrow();

        assertThat(country).isEqualTo(new WikidataCountry("Q45", List.of("pt")));
        server.verify();
    }

    @Test
    void an_unknown_country_is_empty_and_a_bad_code_never_reaches_wikidata() {
        answers(bindings());

        assertThat(client.findCountry("QQ")).isEmpty();
        assertThatThrownBy(() -> client.findCountry("PRT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.regions("Q45; DROP")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rows_of_one_item_fold_into_one_with_its_largest_population_and_every_parent() {
        answers(bindings(
                "{" + uri("item", ENTITY + "Q597") + "," + literal("coord", "Point(-9.14 38.72)") + ","
                        + literal("population", "500000") + "," + literal("osm", "5400890") + ","
                        + uri("image", "http://commons.wikimedia.org/wiki/Special:FilePath/Lisbon%20view.jpg") + ","
                        + literal("links", "250") + "," + uri("parent", ENTITY + "Q1") + "}",
                "{" + uri("item", ENTITY + "Q597") + "," + literal("population", "545000.0") + ","
                        + literal("links", "x") + "," + uri("parent", ENTITY + "Q2") + "}",
                "{" + uri("item", ENTITY + "Q8") + "," + literal("coord", "not a point") + "}"));

        List<WikidataItem> cities = client.cities("Q45", 20_000, List.of("Q1", "Q2"));

        assertThat(cities).hasSize(2);
        assertThat(cities.get(0)).isEqualTo(new WikidataItem("Q597", 38.72, -9.14, 545_000L, "5400890",
                "Lisbon_view.jpg", 250, Set.of("Q1", "Q2")));
        assertThat(cities.get(1).latitude()).isNull();
        assertThat(cities.get(1).imageFileName()).isNull();
    }

    @Test
    void the_other_queries_parse_the_same_way() {
        answers(bindings("{" + uri("item", ENTITY + "Q1") + "}"));
        answers(bindings("{" + uri("item", ENTITY + "Q123") + "," + literal("links", "40") + "}"));
        answers(bindings());

        assertThat(client.regions("Q45")).extracting(WikidataItem::id).containsExactly("Q1");
        assertThat(client.placesOfClass("Q45", "Q33506", 5, 300)).extracting(WikidataItem::sitelinks)
                .containsExactly(40);
        assertThat(client.cities("Q45", 1000, List.of())).isEmpty();
    }

    @Test
    void labels_carry_their_language_and_are_asked_for_in_batches() {
        server.expect(content().string(org.hamcrest.Matchers.containsString("pt")))
                .andRespond(withSuccess(bindings("{" + uri("item", ENTITY + "Q597") + ","
                        + "\"label\": {\"type\": \"literal\", \"value\": \"Lisboa\", \"xml:lang\": \"pt\"}}"),
                        MediaType.APPLICATION_JSON));
        answers(bindings());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            many.add("Q" + (i + 1));
        }

        assertThat(client.labels(many, List.of("pt", "e\"n"))).containsExactly(new WikidataLabel("Q597", "pt",
                "Lisboa"));
        server.verify();
    }

    @Test
    void a_rate_limited_or_timed_out_query_is_retried_after_the_wait_wikidata_asks_for() {
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "7"));
        server.expect(method(HttpMethod.POST)).andRespond(withException(new java.net.SocketTimeoutException()));
        answers(bindings());

        assertThat(client.regions("Q45")).isEmpty();

        // The other waits are the pacing between queries.
        assertThat(sleeps).filteredOn(wait -> wait.getSeconds() >= 5)
                .containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(10));
    }

    @Test
    void wikidata_gives_up_after_the_last_attempt_or_at_once_on_a_client_error() {
        server.expect(times(3), method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "soon"));

        assertThatThrownBy(() -> client.regions("Q45")).isInstanceOf(OpenDataException.class)
                .hasMessageContaining("after 3 attempts");

        server.reset();
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.regions("Q45")).hasMessageContaining("HTTP 400");
        server.reset();
        answers("not json");
        assertThatThrownBy(() -> client.regions("Q45")).hasMessageContaining("isn't JSON");
    }

    @Test
    void an_interrupted_wait_stops_the_query() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://query.example/sparql");
        MockRestServiceServer paced = MockRestServiceServer.bindTo(builder).build();
        ImportProperties slow = new ImportProperties("https://query.example/sparql", null, "Test/1", List.of("en"),
                1, 1, 1, 1, 1, 1, 1, 60_000, 3, 1);
        WikidataClient interrupted = new WikidataClient(builder.build(), new ObjectMapper(), slow, duration -> {
            throw new InterruptedException();
        });
        paced.expect(method(HttpMethod.POST)).andRespond(withSuccess(bindings(), MediaType.APPLICATION_JSON));
        interrupted.regions("Q45");

        // The second query has to wait its turn, and the wait is interrupted.
        assertThatThrownBy(() -> interrupted.regions("Q45")).isInstanceOf(OpenDataException.class)
                .hasMessageContaining("Interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }
}
