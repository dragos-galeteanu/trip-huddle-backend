package com.triphuddle.api.license.api;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.dto.PromotionRequest;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIC-3: automatic and coded promotions, their limits and the floor.
 */
class PromotionIT extends LicenseApiSupport {

    private final Instant now = Instant.now();

    @Test
    void anAutomaticPromotionLowersTheCataloguePriceWhileItRuns() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        create(license, LicenseFixtures.automatic(200, now.minusSeconds(60), now.plus(Duration.ofDays(7))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.discountedPriceMinor").value(800));

        mvc.perform(get(LICENSES + "/" + license)).andExpect(jsonPath("$.promotedPriceMinor").value(800));
        mvc.perform(get(LICENSES + "/" + license + "/price")).andExpect(jsonPath("$.priceMinor").value(800))
                .andExpect(jsonPath("$.listPriceMinor").value(1000));
    }

    @Test
    void automaticRunsCannotOverlapButCanFollowEachOther() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        Instant inAWeek = now.plus(Duration.ofDays(7));
        create(license, LicenseFixtures.automatic(100, now, inAWeek)).andExpect(status().isCreated());

        create(license, LicenseFixtures.automatic(50, now.plus(Duration.ofDays(3)), null))
                .andExpect(status().isUnprocessableEntity());
        create(license, LicenseFixtures.automatic(50, inAWeek, null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SCHEDULED"));
    }

    @Test
    void aCodeReplacesTheAutomaticPromotionAndNeverGoesBelowTheFloor() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        create(license, LicenseFixtures.automatic(100, now.minusSeconds(60), null)).andExpect(status().isCreated());
        PromotionRequest deep = LicenseFixtures.coded("deep90", DiscountType.PERCENTAGE, 90);
        deep.setMinPriceMinor(300L);
        create(license, deep).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("DEEP90"));

        mvc.perform(get(LICENSES + "/" + license + "/price").param("code", " deep90 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceMinor").value(300))
                .andExpect(jsonPath("$.promotion.code").value("DEEP90"));
        mvc.perform(get(LICENSES + "/" + license + "/price").param("code", "NOPE"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aCodeIsUniquePerLicenseAndAPercentageAtMostAHundred() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        create(license, LicenseFixtures.coded("SPRING", DiscountType.AMOUNT, 100)).andExpect(status().isCreated());

        create(license, LicenseFixtures.coded("spring", DiscountType.AMOUNT, 50))
                .andExpect(status().isUnprocessableEntity());
        create(license, LicenseFixtures.coded("TOOMUCH", DiscountType.PERCENTAGE, 101))
                .andExpect(status().isUnprocessableEntity());
        PromotionRequest backwards = LicenseFixtures.automatic(10, now, now.minusSeconds(1));
        create(license, backwards).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aRedeemedPromotionKeepsItsTermsAndCanOnlyBeEnded() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest request = LicenseFixtures.coded("ONCE", DiscountType.AMOUNT, 100);
        UUID promotion = idOf(body(create(license, request).andExpect(status().isCreated())));
        subscribe(UUID.randomUUID(), license, null, "ONCE").andExpect(status().isCreated());

        request.setDiscountValue(500L);
        send(put(promotions(license) + "/" + promotion), request, staff).andExpect(status().isUnprocessableEntity());
        request.setDiscountValue(100L);
        request.setName("Renamed");
        send(put(promotions(license) + "/" + promotion), request, staff).andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptionCount").value(1));
        mvc.perform(delete(promotions(license) + "/" + promotion).with(staff))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(promotions(license) + "/" + promotion + "/end").with(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ENDED"));
        mvc.perform(post(promotions(license) + "/" + promotion + "/end").with(staff))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aScheduledPromotionIsDeletedRatherThanEnded() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        UUID promotion = idOf(body(create(license, LicenseFixtures.automatic(100, now.plus(Duration.ofDays(1)), null))));

        mvc.perform(post(promotions(license) + "/" + promotion + "/end").with(staff))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete(promotions(license) + "/" + promotion).with(staff)).andExpect(status().isOk());
        mvc.perform(get(promotions(license) + "/" + promotion).with(staff)).andExpect(status().isNotFound());
    }

    @Test
    void redemptionLimitsOncePerCustomerAndNewCustomersOnlyHold() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest limited = LicenseFixtures.coded("TWO", DiscountType.AMOUNT, 100);
        limited.setMaxRedemptions(1);
        create(license, limited).andExpect(status().isCreated());
        PromotionRequest welcome = LicenseFixtures.coded("WELCOME", DiscountType.AMOUNT, 100);
        welcome.setIsNewCustomersOnly(true);
        welcome.setIsOncePerCustomer(true);
        create(license, welcome).andExpect(status().isCreated());

        subscribe(UUID.randomUUID(), license, null, "TWO").andExpect(status().isCreated());
        subscribe(UUID.randomUUID(), license, null, "TWO").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("used up")));

        UUID returning = UUID.randomUUID();
        activated(returning, publishedLicense(LicenseTarget.USER, 500));
        subscribe(returning, license, null, "WELCOME").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("new customers")));
    }

    @Test
    void onlyStaffSeeAndManagePromotionsAndInspectorsOnlyRead() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);

        send(post(promotions(license)), LicenseFixtures.coded("X1X", DiscountType.AMOUNT, 1), Caller.INSPECTOR.request())
                .andExpect(status().isForbidden());
        mvc.perform(get(promotions(license)).param("page", "1").param("pageSize", "5")
                .with(Caller.INSPECTOR.request())).andExpect(status().isOk());
        mvc.perform(get(promotions(license)).param("page", "1").param("pageSize", "5")
                .with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        mvc.perform(get(promotions(UUID.randomUUID())).param("page", "1").param("pageSize", "5").with(staff))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions create(UUID license, PromotionRequest request)
            throws Exception {
        return send(post(promotions(license)), request, staff);
    }

    private static String promotions(UUID license) {
        return LICENSES + "/" + license + "/promotions";
    }
}
