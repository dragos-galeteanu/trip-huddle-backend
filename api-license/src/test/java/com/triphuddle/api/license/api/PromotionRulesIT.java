package com.triphuddle.api.license.api;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.dto.PromotionRequest;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIC-3: when a code can be used, by whom, and how a promotion may change.
 */
class PromotionRulesIT extends LicenseApiSupport {

    private final Instant now = Instant.now();

    @Test
    void aCodeIsRefusedBeforeItStartsAndAfterItEnds() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest later = LicenseFixtures.coded("LATER", DiscountType.AMOUNT, 100);
        later.setStartsAt(now.plus(Duration.ofDays(1)));
        create(license, later).andExpect(status().isCreated());
        PromotionRequest over = LicenseFixtures.coded("OVER", DiscountType.AMOUNT, 100);
        over.setStartsAt(now.minus(Duration.ofDays(2)));
        over.setEndsAt(now.minus(Duration.ofDays(1)));
        create(license, over).andExpect(status().isCreated());

        quote(license, "LATER", null, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Promotion code LATER has not started yet"));
        quote(license, "OVER", null, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Promotion code OVER has ended"));
    }

    @Test
    void aOncePerCustomerCodeIsRefusedToWhoeverUsedItAndOfferedToOthers() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest once = LicenseFixtures.coded("JUSTONCE", DiscountType.AMOUNT, 100);
        once.setIsOncePerCustomer(true);
        create(license, once).andExpect(status().isCreated());
        UUID user = UUID.randomUUID();
        subscribe(user, license, null, "JUSTONCE").andExpect(status().isCreated());

        quote(license, "JUSTONCE", null, Caller.CUSTOMER.request(user)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Promotion code JUSTONCE was already used"));
        quote(license, "JUSTONCE", null, Caller.CUSTOMER.request()).andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMinor").value(900));
    }

    @Test
    void aGroupQuoteChecksTheGroupsHistoryOnlyForItsAdmins() throws Exception {
        UUID license = publishedLicense(LicenseTarget.GROUP, 1000);
        PromotionRequest welcome = LicenseFixtures.coded("NEWGROUP", DiscountType.AMOUNT, 100);
        welcome.setIsNewCustomersOnly(true);
        create(license, welcome).andExpect(status().isCreated());
        UUID admin = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        makeGroupAdmin(group, admin);
        UUID earlier = idOf(body(subscribe(admin, publishedLicense(LicenseTarget.GROUP, 500), group, null)
                .andExpect(status().isCreated())));
        mvc.perform(post(SUBSCRIPTIONS + "/" + earlier + "/activate").with(staff)).andExpect(status().isOk());

        quote(license, "NEWGROUP", group, Caller.CUSTOMER.request(admin)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("new customers only")));
        // An outsider learns nothing of the group's history.
        quote(license, "NEWGROUP", group, Caller.CUSTOMER.request()).andExpect(status().isOk());
        quote(license, "NEWGROUP", null, Caller.CUSTOMER.request(admin)).andExpect(status().isOk());
    }

    @Test
    void anAutomaticPromotionTheSubscriberMayNotUseIsSkippedRatherThanRefused() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest firstTime = LicenseFixtures.automatic(300, now.minusSeconds(60), null);
        firstTime.setIsNewCustomersOnly(true);
        create(license, firstTime).andExpect(status().isCreated());
        UUID returning = UUID.randomUUID();
        activated(returning, publishedLicense(LicenseTarget.USER, 500));

        quote(license, null, null, Caller.CUSTOMER.request(returning)).andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMinor").value(1000));
        quote(license, null, null, Caller.CUSTOMER.request()).andExpect(jsonPath("$.priceMinor").value(700));
    }

    @Test
    void staffAndInspectorsReadOnePromotion() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        UUID promotion = idOf(body(create(license, LicenseFixtures.coded("READ", DiscountType.AMOUNT, 100))));

        mvc.perform(get(promotions(license) + "/" + promotion).with(Caller.INSPECTOR.request()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("READ"))
                .andExpect(jsonPath("$.discountedPriceMinor").value(900));
        mvc.perform(get(promotions(license) + "/" + promotion).with(Caller.CUSTOMER.request()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anArchivedLicenseTakesNoNewPromotions() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        mvc.perform(post(LICENSES + "/" + license + "/archive").with(staff)).andExpect(status().isOk());

        create(license, LicenseFixtures.coded("TOOLATE", DiscountType.AMOUNT, 100))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("is archived")));
    }

    @Test
    void anUnredeemedPromotionsTermsChangeFreely() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest request = LicenseFixtures.coded("FREE", DiscountType.AMOUNT, 100);
        UUID promotion = idOf(body(create(license, request)));

        request.setDiscountType(DiscountType.PERCENTAGE);
        request.setDiscountValue(50L);
        request.setDurationPeriods(3);
        send(put(promotions(license) + "/" + promotion), request, staff).andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountedPriceMinor").value(500));
    }

    @Test
    void aRedeemedPromotionRefusesEachChangeOfTerms() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        UUID promotion = idOf(body(create(license, kept())));
        subscribe(UUID.randomUUID(), license, null, "KEPT").andExpect(status().isCreated());
        String path = promotions(license) + "/" + promotion;

        PromotionRequest code = kept();
        code.setCode("OTHER");
        PromotionRequest type = kept();
        type.setDiscountType(DiscountType.PERCENTAGE);
        PromotionRequest value = kept();
        value.setDiscountValue(200L);
        PromotionRequest started = kept();
        started.setStartsAt(now.minus(Duration.ofDays(3)));
        PromotionRequest periods = kept();
        periods.setDurationPeriods(2);
        PromotionRequest once = kept();
        once.setIsOncePerCustomer(true);
        PromotionRequest fresh = kept();
        fresh.setIsNewCustomersOnly(true);
        PromotionRequest floor = kept();
        floor.setMinPriceMinor(500L);
        for (PromotionRequest changed : java.util.List.of(code, type, value, started, periods, once, fresh, floor)) {
            send(put(path), changed, staff).andExpect(status().isUnprocessableEntity());
        }
        PromotionRequest renamed = kept();
        renamed.setName("Renamed");
        send(put(path), renamed, staff).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"));
    }

    // The same terms each time, so a test changes one of them.
    private PromotionRequest kept() {
        PromotionRequest request = LicenseFixtures.coded("KEPT", DiscountType.AMOUNT, 100);
        request.setStartsAt(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minusSeconds(60));
        return request;
    }

    @Test
    void maxRedemptionsCantGoBelowTheRedemptionsSoFar() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        PromotionRequest request = LicenseFixtures.coded("POPULAR", DiscountType.AMOUNT, 100);
        UUID promotion = idOf(body(create(license, request)));
        subscribe(UUID.randomUUID(), license, null, "POPULAR").andExpect(status().isCreated());
        subscribe(UUID.randomUUID(), license, null, "POPULAR").andExpect(status().isCreated());

        request.setMaxRedemptions(1);
        send(put(promotions(license) + "/" + promotion), request, staff).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Max redemptions can't be below the 2 redemptions so far"));
        request.setMaxRedemptions(2);
        send(put(promotions(license) + "/" + promotion), request, staff).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EXHAUSTED"));
    }

    private ResultActions quote(UUID license, String code, UUID group,
                                org.springframework.test.web.servlet.request.RequestPostProcessor caller)
            throws Exception {
        var request = get(LICENSES + "/" + license + "/price");
        if (code != null) {
            request.param("code", code);
        }
        if (group != null) {
            request.param("groupId", group.toString());
        }
        return mvc.perform(caller == null ? request : request.with(caller));
    }

    private ResultActions create(UUID license, PromotionRequest request) throws Exception {
        return send(post(promotions(license)), request, staff);
    }

    private static String promotions(UUID license) {
        return LICENSES + "/" + license + "/promotions";
    }
}
