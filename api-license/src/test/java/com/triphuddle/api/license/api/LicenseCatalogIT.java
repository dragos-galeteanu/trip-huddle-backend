package com.triphuddle.api.license.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.dto.CreateLicenseRequest;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIC-1 and LIC-2: the license lifecycle, the public catalogue and entitlements.
 */
class LicenseCatalogIT extends LicenseApiSupport {

    @Test
    void aDraftIsHiddenFromCustomersUntilPublished() throws Exception {
        UUID id = draft(LicenseTarget.USER, 999);

        mvc.perform(get(LICENSES + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(LICENSES + "/" + id).with(Caller.CUSTOMER.request())).andExpect(status().isNotFound());
        mvc.perform(get(LICENSES + "/" + id).with(Caller.INSPECTOR.request())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        published.assertNothingPublished();

        mvc.perform(post(LICENSES + "/" + id + "/publish").with(staff)).andExpect(status().isOk());

        mvc.perform(get(LICENSES + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.entitlements.length()").value(2));
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(LicenseCatalogChangedEvent.class,
                        e -> assertThat(e.status()).isEqualTo("PUBLISHED")));
    }

    @Test
    void theCatalogueListsOnlyPublishedLicensesToCustomers() throws Exception {
        UUID draft = draft(LicenseTarget.USER, 999);
        UUID live = publishedLicense(LicenseTarget.USER, 999);

        JsonNode list = body(mvc.perform(get(LICENSES).param("page", "1").param("pageSize", "100"))
                .andExpect(status().isOk()));

        List<String> ids = list.path("data").findValuesAsText("id");
        assertThat(ids).contains(live.toString()).doesNotContain(draft.toString());
    }

    @Test
    void onlyADraftChangesItsCodeAndTargetOrIsDeleted() throws Exception {
        UUID live = publishedLicense(LicenseTarget.USER, 999);
        Map<String, Object> rename = updateOf(LicenseFixtures.freshCode(), LicenseTarget.USER, 1299);

        send(put(LICENSES + "/" + live), rename, staff).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete(LICENSES + "/" + live).with(staff)).andExpect(status().isUnprocessableEntity());

        UUID draft = draft(LicenseTarget.USER, 999);
        send(put(LICENSES + "/" + draft), updateOf(LicenseFixtures.freshCode(), LicenseTarget.GROUP, 1299), staff)
                .andExpect(status().isOk()).andExpect(jsonPath("$.target").value("GROUP"));
        mvc.perform(delete(LICENSES + "/" + draft).with(staff)).andExpect(status().isOk());
        mvc.perform(get(LICENSES + "/" + draft).with(staff)).andExpect(status().isNotFound());
    }

    @Test
    void aPublishedLicensesNewPriceIsAnnouncedButItsCodeStays() throws Exception {
        UUID live = publishedLicense(LicenseTarget.USER, 999);
        String code = jdbc.queryForObject("select code from licenses where id = ?", String.class, live);
        published.reset();

        send(put(LICENSES + "/" + live), updateOf(code, LicenseTarget.USER, 1299), staff)
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceMinor").value(1299))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(LicenseCatalogChangedEvent.class,
                        e -> assertThat(e.priceMinor()).isEqualTo(1299)));
    }

    @Test
    void archivingStopsNewSubscriptionsButKeepsTheLicenseReadableAndRepublishable() throws Exception {
        UUID id = publishedLicense(LicenseTarget.USER, 999);

        mvc.perform(post(LICENSES + "/" + id + "/archive").with(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        mvc.perform(post(LICENSES + "/" + id + "/archive").with(staff)).andExpect(status().isUnprocessableEntity());
        subscribe(UUID.randomUUID(), id, null, null).andExpect(status().isUnprocessableEntity());
        send(put(LICENSES + "/" + id + "/entitlements"), Map.of("entitlements", List.of()), staff)
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post(LICENSES + "/" + id + "/publish").with(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isEmpty());
        mvc.perform(post(LICENSES + "/" + id + "/publish").with(staff)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void codesAreUniqueAmongLicensesNotDeleted() throws Exception {
        CreateLicenseRequest request = LicenseFixtures.createLicense(LicenseTarget.USER, 999);
        UUID first = idOf(body(send(post(LICENSES), request, staff).andExpect(status().isCreated())));

        send(post(LICENSES), request, staff).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete(LICENSES + "/" + first).with(staff)).andExpect(status().isOk());
        send(post(LICENSES), request, staff).andExpect(status().isCreated());
    }

    @Test
    void aPublishedLicensesTargetCantChangeEvenWithTheSameCode() throws Exception {
        UUID live = publishedLicense(LicenseTarget.USER, 999);
        String code = jdbc.queryForObject("select code from licenses where id = ?", String.class, live);

        send(put(LICENSES + "/" + live), updateOf(code, LicenseTarget.GROUP, 999), staff)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aDraftIsQuotedToStaffOnly() throws Exception {
        UUID draft = draft(LicenseTarget.USER, 999);

        mvc.perform(get(LICENSES + "/" + draft + "/price").with(Caller.CUSTOMER.request()))
                .andExpect(status().isNotFound());
        mvc.perform(get(LICENSES + "/" + draft + "/price").with(Caller.INSPECTOR.request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceMinor").value(999));
    }

    @Test
    void staffFilterTheCatalogueByStatusAndAPageWithNothingOnItIsEmpty() throws Exception {
        UUID draft = draft(LicenseTarget.USER, 999);

        JsonNode drafts = body(mvc.perform(get(LICENSES).param("page", "1").param("pageSize", "100")
                .param("status", "DRAFT").with(staff)).andExpect(status().isOk()));
        assertThat(drafts.path("data").findValuesAsText("status")).containsOnly("DRAFT");
        assertThat(drafts.path("data").findValuesAsText("id")).contains(draft.toString());
        mvc.perform(get(LICENSES).param("page", "10000").param("pageSize", "100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aBlankDescriptionOrEntitlementValueIsStoredAsNothing() throws Exception {
        CreateLicenseRequest request = LicenseFixtures.createLicense(LicenseTarget.USER, 999);
        request.setDescription("   ");
        UUID id = idOf(body(send(post(LICENSES), request, staff).andExpect(status().isCreated())));

        send(put(LICENSES + "/" + id + "/entitlements"), Map.of("entitlements", List.of(
                Map.of("key", "VISA_GUIDANCE", "value", "  "))), staff).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.entitlements[0].value").doesNotExist());
    }

    @Test
    void entitlementsAreReplacedInPlaceAndAnnouncedToSubscribers() throws Exception {
        UUID id = publishedLicense(LicenseTarget.USER, 999);
        published.reset();

        send(put(LICENSES + "/" + id + "/entitlements"), Map.of("entitlements", List.of(
                Map.of("key", "AI_ITINERARIES_PER_MONTH", "value", "10"),
                Map.of("key", "VISA_GUIDANCE"))), staff)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitlements.length()").value(2));
        send(put(LICENSES + "/" + id + "/entitlements"), Map.of("entitlements", List.of(
                Map.of("key", "VISA_GUIDANCE"), Map.of("key", "VISA_GUIDANCE"))), staff)
                .andExpect(status().isUnprocessableEntity());

        assertThat(published.published()).extracting(Published::payload).filteredOn(
                LicenseEntitlementsChangedEvent.class::isInstance).singleElement().satisfies(p ->
                assertThat(((LicenseEntitlementsChangedEvent) p).entitlements())
                        .containsEntry("AI_ITINERARIES_PER_MONTH", "10").containsKey("VISA_GUIDANCE")
                        .doesNotContainKey("DESTINATION_ACCESS"));
    }

    @Test
    void onlyStaffManageTheCatalogue() throws Exception {
        send(post(LICENSES), LicenseFixtures.createLicense(LicenseTarget.USER, 999), Caller.CUSTOMER.request())
                .andExpect(status().isForbidden());
        send(post(LICENSES), LicenseFixtures.createLicense(LicenseTarget.USER, 999), Caller.INSPECTOR.request())
                .andExpect(status().isForbidden());
        mvc.perform(post(LICENSES + "/" + UUID.randomUUID() + "/publish")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesInvalidLicenses() throws Exception {
        CreateLicenseRequest lowerCase = LicenseFixtures.createLicense(LicenseTarget.USER, 999);
        lowerCase.setCode("plan");
        CreateLicenseRequest negative = LicenseFixtures.createLicense(LicenseTarget.USER, -1);

        send(post(LICENSES), lowerCase, staff).andExpect(status().isBadRequest());
        send(post(LICENSES), negative, staff).andExpect(status().isBadRequest());
        mvc.perform(get(LICENSES + "/" + UUID.randomUUID()).with(staff)).andExpect(status().isNotFound());
    }

    private static Map<String, Object> updateOf(String code, LicenseTarget target, long priceMinor) {
        return Map.of("code", code, "name", "Explorer+", "target", target, "priceMinor", priceMinor,
                "billingInterval", "MONTH");
    }
}
