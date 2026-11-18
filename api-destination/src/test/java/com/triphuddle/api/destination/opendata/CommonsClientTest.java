package com.triphuddle.api.destination.opendata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.api.destination.visa.VisaDatasetClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pins Commons' answer parsing and the licence filter, and the plain downloads, against mocked HTTP. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommonsClientTest {

    MockRestServiceServer server;
    CommonsClient commons;
    VisaDatasetClient visas;

    @BeforeEach
    void clients() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://commons.example/w/api.php");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        commons = new CommonsClient(client, new ObjectMapper());
        visas = new VisaDatasetClient(client);
    }

    static String page(String title, String license, String extra) {
        return """
                {"title": "%s", "imageinfo": [{"url": "https://upload.example/full.jpg", "width": 4000, "height": 3000,
                 "mime": "image/jpeg", "descriptionurl": "https://commons.example/wiki/%s"%s,
                 "extmetadata": {"LicenseShortName": {"value": "%s"},
                   "Artist": {"value": "<a href=\\"/wiki/User:Ana\\">Ana &amp; Bob</a>"},
                   "LicenseUrl": {"value": "https://creativecommons.org/licenses/by-sa/4.0"},
                   "AttributionRequired": {"value": "false"}}}]}""".formatted(title, title, extra, license);
    }

    @Test
    void free_images_are_kept_under_the_names_asked_for_and_others_left_out() {
        server.expect(queryParam("iiurlwidth", "1600")).andRespond(withSuccess("""
                {"query": {"normalized": [{"from": "File:Lisbon view.jpg", "to": "File:Lisbon View.jpg"}],
                 "pages": [%s, %s, {"title": "File:Gone.jpg", "missing": true}, {"title": "File:Unasked.jpg"}]}}"""
                .formatted(page("File:Lisbon View.jpg", "CC BY-SA 4.0",
                                ", \"thumburl\": \"https://upload.example/1600.jpg\", \"thumbwidth\": 1600, "
                                        + "\"thumbheight\": 1200"),
                        page("File:Logo.png", "Fair use", "")), MediaType.APPLICATION_JSON));

        Map<String, CommonsImage> images = commons.images(List.of("Lisbon_view.jpg", "Logo.png", "Gone.jpg"), 1600);

        assertThat(images).containsOnlyKeys("Lisbon_view.jpg");
        CommonsImage image = images.get("Lisbon_view.jpg");
        assertThat(image.url()).isEqualTo("https://upload.example/1600.jpg");
        assertThat(image.width()).isEqualTo(1600);
        assertThat(image.author()).isEqualTo("Ana & Bob");
        assertThat(image.isAttributionRequired()).isFalse();
    }

    @Test
    void an_image_without_a_thumbnail_uses_its_original_and_files_go_in_batches_of_50() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("action=query"))).andRespond(withSuccess("""
                {"query": {"pages": [%s]}}""".formatted(page("File:A.jpg", "CC0", "")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("action=query")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        List<String> files = new ArrayList<>(List.of("A.jpg"));
        for (int i = 0; i < 50; i++) {
            files.add("F" + i + ".jpg");
        }

        Map<String, CommonsImage> images = commons.images(files, 800);

        assertThat(images.get("A.jpg").url()).isEqualTo("https://upload.example/full.jpg");
        assertThat(images.get("A.jpg").width()).isEqualTo(4000);
        server.verify();
    }

    @Test
    void a_failing_commons_fails_the_query() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("action=query")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> commons.images(List.of("A.jpg"), 800)).isInstanceOf(OpenDataException.class);

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("action=query")))
                .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> commons.images(List.of("A.jpg"), 800)).isInstanceOf(OpenDataException.class);
    }

    @Test
    void a_download_must_bring_bytes() {
        server.expect(requestTo("https://upload.example/a.jpg"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo("https://upload.example/empty.jpg"))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_JPEG));
        server.expect(requestTo("https://upload.example/broken.jpg"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(commons.download("https://upload.example/a.jpg")).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> commons.download("https://upload.example/empty.jpg"))
                .hasMessageContaining("empty or too large");
        assertThatThrownBy(() -> commons.download("https://upload.example/broken.jpg"))
                .hasMessageContaining("failed");
    }

    @Test
    void the_visa_dataset_must_not_be_empty() {
        server.expect(requestTo("https://data.example/visas.csv"))
                .andRespond(withSuccess("Passport,Destination,Requirement", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://data.example/empty.csv")).andRespond(withSuccess(" ", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://data.example/down.csv")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(visas.download("https://data.example/visas.csv")).startsWith("Passport");
        assertThatThrownBy(() -> visas.download("https://data.example/empty.csv")).hasMessageContaining("empty");
        assertThatThrownBy(() -> visas.download("https://data.example/down.csv")).hasMessageContaining("Could not");
    }
}
