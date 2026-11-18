package com.triphuddle.api.destination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.triphuddle.api.destination.dto.VisaRuleImportResponse;
import com.triphuddle.api.destination.opendata.CommonsImage;
import com.triphuddle.api.destination.service.PhotoService;
import com.triphuddle.api.destination.visa.VisaRuleImportService;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * DST-3 and DST-7 with what production sets up: a bucket that keeps copies of the photos (a mock of Google Cloud
 * Storage), and a visa dataset counted as full from one rule, so a full import's removals can be seen.
 */
@TestPropertySource(properties = {
        "app.destination.photos.bucket=photos-test",
        "app.destination.visas.min-rules-for-full-import=1"})
class BucketAndFullImportIT extends DestinationApiSupport {

    @MockBean Storage storage;
    @Autowired PhotoService photos;
    @Autowired VisaRuleImportService visaImports;

    @Test
    void aPhotoIsCopiedToTheBucketAndServedBySignedUrl() throws Exception {
        country("PT");
        UUID lisbon = idOf(place("PT", "CITY", "Lisbon", null));
        given(commons.images(any(), anyInt())).willReturn(Map.of("Tram.png", new CommonsImage("Tram.png",
                "https://upload.example/Tram.png", 1600, 1200, "image/png", "Ana", "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/", "https://commons.example/Tram.png", true)));
        given(commons.download(anyString())).willReturn(new byte[] {1, 2, 3});
        given(storage.signUrl(any(BlobInfo.class), eq(360L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption[].class)))
                .willReturn(new URL("https://storage.example/photo?signed"));

        assertThat(photos.addFromCommons(Map.of(lisbon, "Tram.png"))).isEqualTo(1);

        ArgumentCaptor<BlobInfo> stored = ArgumentCaptor.forClass(BlobInfo.class);
        then(storage).should().create(stored.capture(), eq(new byte[] {1, 2, 3}));
        assertThat(stored.getValue().getBucket()).isEqualTo("photos-test");
        assertThat(stored.getValue().getName()).startsWith("places/" + lisbon + "/").endsWith(".png");
        assertThat(stored.getValue().getContentType()).isEqualTo("image/png");
        assertThat(stored.getValue().getCacheControl()).isEqualTo("public, max-age=31536000");
        JsonNode photo = body(call(get(PLACES + "/" + lisbon), Caller.ADMIN, UUID.randomUUID())).get("photos").get(0);
        assertThat(photo.get("url").asText()).isEqualTo("https://storage.example/photo?signed");
    }

    @Test
    void aFullVisaImportRemovesTheRulesTheDatasetNoLongerHas() {
        given(visaDataset.download(anyString())).willReturn("""
                Passport,Destination,Requirement
                RO,TH,30
                DE,TH,visa free
                """);
        visaImports.queue(UUID.randomUUID());
        assertThat(visaImports.runNext().removedCount()).isZero();

        given(visaDataset.download(anyString())).willReturn("""
                Passport,Destination,Requirement
                RO,TH,45
                """);
        visaImports.queue(UUID.randomUUID());
        VisaRuleImportResponse second = visaImports.runNext();

        assertThat(second.removedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from destination_visa_rules", Integer.class)).isEqualTo(1);
    }
}
