package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.api.destination.job.ImportJobRunner;
import com.triphuddle.api.destination.opendata.CommonsImage;
import com.triphuddle.api.destination.opendata.OpenDataException;
import com.triphuddle.api.destination.opendata.WikidataCountry;
import com.triphuddle.api.destination.opendata.WikidataItem;
import com.triphuddle.api.destination.opendata.WikidataLabel;
import com.triphuddle.api.destination.service.ImportService;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DST-3: imports from Wikidata and Commons, with Wikimedia answered by mocks; and the category tree. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ImportsAndCategoriesIT extends DestinationApiSupport {

    static final String IMPORTS = "/api/v1/destinations/imports";
    static final String CATEGORIES = "/api/v1/destinations/categories";

    @Autowired ImportService imports;
    @Autowired ImportJobRunner importJobRunner;

    @BeforeEach
    void portugal() {
        country("PT");
    }

    static WikidataItem item(String id, Double latitude, Double longitude, Long population, String image,
                             String... parents) {
        return new WikidataItem(id, latitude, longitude, population, id.substring(1), image, 10, Set.of(parents));
    }

    static CommonsImage image(String fileName) {
        return new CommonsImage(fileName, "https://upload.example/" + fileName, 1600, 1200, "image/jpeg", "Ana",
                "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://commons.example/" + fileName, true);
    }

    JsonNode category(String slug, UUID parentId, List<String> classes) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(Map.of("slug", slug, "name", "  " + slug + "  ",
                "wikidataClassIds", classes, "description", " Things "));
        if (parentId != null) {
            request.put("parentId", parentId);
        }
        return body(call(post(CATEGORIES), Caller.ADMIN, UUID.randomUUID(), request).andExpect(status().isCreated()));
    }

    void wikimediaKnowsPortugal() {
        given(wikidata.findCountry("PT")).willReturn(Optional.of(new WikidataCountry("Q45", List.of("pt"))));
        given(wikidata.regions("Q45")).willReturn(List.of(item("Q1", null, null, null, null)));
        given(wikidata.cities(eq("Q45"), anyLong(), anyCollection())).willReturn(List.of(
                item("Q597", 38.72, -9.14, 545_000L, "Lisbon.jpg", "Q1"),
                item("Q999", 41.0, -8.0, 30_000L, null, "Q1")));
        given(wikidata.placesOfClass(eq("Q45"), eq("Q33506"), anyInt(), anyInt())).willReturn(List.of(
                item("Q123", 38.70, -9.13, null, "Museum.jpg")));
        given(wikidata.labels(anyCollection(), anyCollection())).willReturn(List.of(
                new WikidataLabel("Q1", "pt", "Região de Lisboa"),
                new WikidataLabel("Q597", "en", "Lisbon"),
                new WikidataLabel("Q597", "pt", "Lisboa"),
                new WikidataLabel("Q123", "en", "National Museum"),
                new WikidataLabel("Q999", "xx-long", " ")));
        given(commons.images(any(), anyInt())).willReturn(Map.of("Lisbon.jpg", image("Lisbon.jpg"),
                "Museum.jpg", image("Museum.jpg")));
    }

    UUID requestImport(Map<String, Object> request) throws Exception {
        return idOf(body(call(post(IMPORTS), Caller.ADMIN, UUID.randomUUID(), request)
                .andExpect(status().isAccepted())));
    }

    JsonNode job(UUID id) throws Exception {
        return body(call(get(IMPORTS + "/" + id), Caller.INSPECTOR, UUID.randomUUID()).andExpect(status().isOk()));
    }

    @Nested
    class Imports {

        @Test
        void an_import_loads_regions_cities_under_them_and_notable_places_with_photos() throws Exception {
            category("museums", null, List.of("Q33506"));
            wikimediaKnowsPortugal();
            UUID id = requestImport(Map.of("countryCode", "pt"));

            importJobRunner.runWaiting();

            JsonNode job = job(id);
            assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(job.get("countryWikidataId").asText()).isEqualTo("Q45");
            assertThat(job.get("placesCreated").asInt()).isEqualTo(3);
            assertThat(job.get("photosAdded").asInt()).isEqualTo(2);
            JsonNode lisbon = body(call(get(PLACES + "/slug/pt-lisbon"), Caller.ADMIN, UUID.randomUUID()));
            assertThat(lisbon.get("ancestors").findValuesAsText("name")).containsExactly("Região de Lisboa");
            assertThat(lisbon.get("osmId").asText()).isEqualTo("relation/597");
            assertThat(lisbon.get("photos").get(0).get("isPrimary").asBoolean()).isTrue();
            assertThat(lisbon.get("photos").get(0).get("url").asText()).isEqualTo("https://upload.example/Lisbon.jpg");
            JsonNode museum = body(call(get(PLACES + "/slug/pt-national-museum"), Caller.ADMIN, UUID.randomUUID()));
            assertThat(museum.get("ancestors").findValuesAsText("name")).contains("Lisbon");
            assertThat(museum.get("categories").findValuesAsText("slug")).containsExactly("museums");
        }

        @Test
        void importing_again_updates_the_places_it_already_has_and_skips_deleted_ones() throws Exception {
            category("museums", null, List.of("Q33506"));
            wikimediaKnowsPortugal();
            requestImport(Map.of("countryCode", "PT"));
            imports.runNext();
            UUID museum = idOf(body(call(get(PLACES + "/slug/pt-national-museum"), Caller.ADMIN, UUID.randomUUID())));
            call(delete(PLACES + "/" + museum), Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNoContent());

            UUID again = requestImport(Map.of("countryCode", "PT", "isIncludingPhotos", false));
            imports.runNext();

            JsonNode job = job(again);
            assertThat(job.get("placesCreated").asInt()).isZero();
            assertThat(job.get("placesUpdated").asInt()).isEqualTo(2);
            assertThat(job.get("photosAdded").asInt()).isZero();
        }

        @Test
        void importing_a_place_staff_made_fills_in_what_it_lacks_and_keeps_what_staff_set() throws Exception {
            UUID museums = idOf(category("museums", null, List.of("Q33506")));
            wikimediaKnowsPortugal();
            UUID made = idOf(place("PT", "POI", "Museu Nacional", null, "wikidataId", "Q123"));

            requestImport(Map.of("countryCode", "PT", "isIncludingPhotos", false));
            imports.runNext();

            JsonNode museum = body(call(get(PLACES + "/" + made), Caller.ADMIN, UUID.randomUUID()));
            assertThat(museum.get("name").asText()).as("staff's name stays").isEqualTo("Museu Nacional");
            assertThat(museum.get("latitude").asDouble()).isEqualTo(38.70);
            assertThat(museum.get("longitude").asDouble()).isEqualTo(-9.13);
            assertThat(museum.get("osmId").asText()).isEqualTo("relation/123");
            assertThat(museum.get("names").findValuesAsText("name")).contains("National Museum");
            assertThat(museum.get("categories").findValuesAsText("id")).containsExactly(museums.toString());

            requestImport(Map.of("countryCode", "PT", "isIncludingPhotos", false));
            imports.runNext();
            JsonNode again = body(call(get(PLACES + "/" + made), Caller.ADMIN, UUID.randomUUID()));
            assertThat(again.get("names").findValuesAsText("name")).as("a known name is not added twice")
                    .containsOnlyOnce("National Museum");
        }

        @Test
        void a_failing_wikidata_fails_the_import_with_its_reason() throws Exception {
            given(wikidata.findCountry("PT")).willThrow(new OpenDataException("Wikidata answered 429"));
            UUID failing = requestImport(Map.of("countryCode", "PT", "isIncludingPois", false));
            imports.runNext();

            assertThat(job(failing).get("status").asText()).isEqualTo("FAILED");
            assertThat(job(failing).get("error").asText()).contains("429");

            willReturn(Optional.empty()).given(wikidata).findCountry("PT");
            UUID unknown = requestImport(Map.of("countryCode", "PT"));
            imports.runNext();
            assertThat(job(unknown).get("error").asText()).contains("no country");
            assertThat(imports.runNext()).isNull();
        }

        @Test
        void one_import_per_country_at_a_time_and_only_of_active_countries() throws Exception {
            requestImport(Map.of("countryCode", "PT", "minCityPopulation", 50_000, "minPoiSitelinks", 20));

            call(post(IMPORTS), Caller.ADMIN, UUID.randomUUID(), Map.of("countryCode", "PT"))
                    .andExpect(status().isUnprocessableEntity());
            call(post(IMPORTS), Caller.ADMIN, UUID.randomUUID(), Map.of("countryCode", "FR"))
                    .andExpect(status().isUnprocessableEntity());
            call(post(IMPORTS), Caller.ADMIN, UUID.randomUUID(), Map.of("countryCode", "PT", "minPoiSitelinks", 0))
                    .andExpect(status().isBadRequest());
            call(post(IMPORTS), Caller.INSPECTOR, UUID.randomUUID(), Map.of("countryCode", "PT"))
                    .andExpect(status().isForbidden());

            JsonNode list = body(call(get(IMPORTS + "?page=1&pageSize=10&countryCode=pt&status=QUEUED"),
                    Caller.SUPPORT, UUID.randomUUID()).andExpect(status().isOk()));
            assertThat(list.get("data")).hasSize(1);
            assertThat(list.get("data").get(0).get("minCityPopulation").asLong()).isEqualTo(50_000);
            call(get(IMPORTS + "/" + UUID.randomUUID()), Caller.SUPPORT, UUID.randomUUID())
                    .andExpect(status().isNotFound());
            call(get(IMPORTS + "?page=1&pageSize=10"), Caller.CUSTOMER, subscriber()).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Photos {

        @Test
        void staff_choose_the_primary_photo_and_removing_it_promotes_the_next() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            given(commons.images(any(), anyInt())).willReturn(Map.of("a.jpg", image("a.jpg"), "b.jpg", image("b.jpg")));
            given(commons.images(eq(List.of("a.jpg")), anyInt())).willReturn(Map.of("a.jpg", image("a.jpg")));
            photoService().addFromCommons(Map.of(lisbon, "a.jpg"));
            given(commons.images(eq(List.of("b.jpg")), anyInt())).willReturn(Map.of("b.jpg", image("b.jpg")));
            photoService().addFromCommons(Map.of(lisbon, "b.jpg"));
            assertThat(photoService().addFromCommons(Map.of(lisbon, "a.jpg"))).isZero();
            JsonNode photos = body(call(get(PLACES + "/" + lisbon), Caller.ADMIN, UUID.randomUUID())).get("photos");
            String second = photos.get(1).get("id").asText();

            call(post(PLACES + "/" + lisbon + "/photos/" + second + "/primary"), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '%s')].isPrimary".formatted(second)).value(true));
            call(delete(PLACES + "/" + lisbon + "/photos/" + second), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isNoContent());

            JsonNode left = body(call(get(PLACES + "/" + lisbon), Caller.ADMIN, UUID.randomUUID())).get("photos");
            assertThat(left).hasSize(1);
            assertThat(left.get(0).get("isPrimary").asBoolean()).isTrue();
            call(delete(PLACES + "/" + lisbon + "/photos/" + UUID.randomUUID()), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isNotFound());
            call(post(PLACES + "/" + idOf(place("PT", "CITY", "Porto", null)) + "/photos/" + left.get(0).get("id")
                    .asText() + "/primary"), Caller.ADMIN, UUID.randomUUID()).andExpect(status().isNotFound());
        }

        @Test
        void a_photo_commons_does_not_return_or_for_a_deleted_place_is_not_added() throws Exception {
            UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
            UUID gone = idOf(place("PT", "CITY", "Gone", null));
            call(delete(PLACES + "/" + gone), Caller.ADMIN, UUID.randomUUID());
            given(commons.images(any(), anyInt())).willReturn(Map.of("g.jpg", image("g.jpg")));

            assertThat(photoService().addFromCommons(Map.of(lisbon, "missing.jpg", gone, "g.jpg"))).isZero();
        }

        com.triphuddle.api.destination.service.PhotoService photoService() {
            return photos;
        }
    }

    @Autowired com.triphuddle.api.destination.service.PhotoService photos;

    @Nested
    class Categories {

        @Test
        void categories_form_a_tree_that_subscribers_read_and_staff_change() throws Exception {
            JsonNode culture = category("culture", null, List.of());
            JsonNode museums = category("museums", idOf(culture), List.of("Q33506", "Q207694"));

            assertThat(museums.get("depth").asInt()).isEqualTo(1);
            assertThat(museums.get("name").asText()).isEqualTo("museums");
            assertThat(museums.get("wikidataClassIds").toString()).isEqualTo("[\"Q207694\",\"Q33506\"]");
            JsonNode tree = body(call(get(CATEGORIES), Caller.CUSTOMER, subscriber()).andExpect(status().isOk()));
            assertThat(tree.findValuesAsText("slug")).containsExactly("culture", "museums");

            call(post(CATEGORIES), Caller.ADMIN, UUID.randomUUID(), Map.of("slug", "culture", "name", "Again"))
                    .andExpect(status().isUnprocessableEntity());
            call(post(CATEGORIES), Caller.CUSTOMER, subscriber(), Map.of("slug", "x", "name", "X"))
                    .andExpect(status().isForbidden());
            call(delete(CATEGORIES + "/" + idOf(culture)), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void moving_a_category_moves_its_subtree_and_deleting_one_unlinks_its_places() throws Exception {
            JsonNode nature = category("nature", null, List.of());
            JsonNode culture = category("culture", null, List.of());
            JsonNode museums = category("museums", idOf(culture), List.of());
            UUID place = idOf(place("PT", "POI", "Museum", null, "categoryIds", List.of(idOf(museums))));

            call(put(CATEGORIES + "/" + idOf(culture)), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("slug", "culture", "name", "Culture", "parentId", idOf(nature)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(nature.get("id").asText()));
            call(put(CATEGORIES + "/" + idOf(nature)), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("slug", "nature", "name", "Nature", "parentId", idOf(museums)))
                    .andExpect(status().isUnprocessableEntity());
            call(put(CATEGORIES + "/" + idOf(museums)), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("slug", "nature", "name", "Taken"))
                    .andExpect(status().isUnprocessableEntity());
            assertThat(body(call(get(CATEGORIES), Caller.ADMIN, UUID.randomUUID())).findValuesAsText("depth"))
                    .contains("2");
            assertThat(names(body(call(get(PLACES + "?page=1&pageSize=5&categoryId=" + idOf(nature)),
                    Caller.ADMIN, UUID.randomUUID())).get("data"))).containsExactly("Museum");

            call(delete(CATEGORIES + "/" + idOf(museums)), Caller.ADMIN, UUID.randomUUID())
                    .andExpect(status().isNoContent());
            assertThat(body(call(get(PLACES + "/" + place), Caller.ADMIN, UUID.randomUUID())).get("categories"))
                    .isEmpty();
            call(put(CATEGORIES + "/" + idOf(museums)), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("slug", "museums", "name", "Gone")).andExpect(status().isNotFound());
            call(put(CATEGORIES + "/" + idOf(culture)), Caller.ADMIN, UUID.randomUUID(),
                    Map.of("slug", "culture", "name", "Culture")).andExpect(jsonPath("$.depth").value(0));
        }
    }
}
