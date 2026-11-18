package com.triphuddle.api.license.fixtures;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.dto.CreateLicenseRequest;
import com.triphuddle.api.license.dto.EntitlementRequest;
import com.triphuddle.api.license.dto.PromotionRequest;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseEntitlement;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valid licenses, promotions, subscriptions and requests, changed by a test only where it cares.
 */
public final class LicenseFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private LicenseFixtures() {
    }

    public static String freshCode() {
        return "PLAN_" + SEQ.incrementAndGet() + "_" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    public static CreateLicenseRequest createLicense(LicenseTarget target, long priceMinor) {
        CreateLicenseRequest request = new CreateLicenseRequest();
        request.setCode(freshCode());
        request.setName("Explorer");
        request.setDescription("Every destination feature");
        request.setTarget(target);
        request.setPriceMinor(priceMinor);
        request.setBillingInterval(BillingInterval.MONTH);
        request.setEntitlements(new ArrayList<>(List.of(
                EntitlementRequest.builder().key("DESTINATION_ACCESS").value("true").build(),
                EntitlementRequest.builder().key("AI_ITINERARIES_PER_MONTH").value("5").build())));
        return request;
    }

    public static PromotionRequest automatic(long discount, Instant startsAt, Instant endsAt) {
        PromotionRequest request = new PromotionRequest();
        request.setName("Spring sale");
        request.setDiscountType(DiscountType.AMOUNT);
        request.setDiscountValue(discount);
        request.setStartsAt(startsAt);
        request.setEndsAt(endsAt);
        return request;
    }

    public static PromotionRequest coded(String code, DiscountType type, long value) {
        PromotionRequest request = automatic(value, Instant.now().minusSeconds(60), null);
        request.setName("Code " + code);
        request.setCode(code);
        request.setDiscountType(type);
        return request;
    }

    public static License license(LicenseTarget target, LicenseStatus status, long priceMinor) {
        License license = new License();
        license.setId(UUID.randomUUID());
        license.setCode(freshCode());
        license.setName("Explorer");
        license.setTarget(target);
        license.setStatus(status);
        license.setPriceMinor(priceMinor);
        license.setBillingInterval(BillingInterval.MONTH);
        LicenseEntitlement access = new LicenseEntitlement();
        access.setLicense(license);
        access.setEntitlementKey("DESTINATION_ACCESS");
        access.setEntitlementValue("true");
        license.getEntitlements().add(access);
        return license;
    }

    public static Promotion promotion(License license, DiscountType type, long value, long floor) {
        Promotion promotion = new Promotion();
        promotion.setId(UUID.randomUUID());
        promotion.setLicense(license);
        promotion.setName("Promo");
        promotion.setDiscountType(type);
        promotion.setDiscountValue(value);
        promotion.setMinPriceMinor(floor);
        promotion.setStartsAt(Instant.parse("2026-01-01T00:00:00Z"));
        return promotion;
    }

    public static Subscription subscription(License license, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setLicense(license);
        subscription.setSubscriberType(license.getTarget());
        subscription.setSubscriberId(UUID.randomUUID());
        subscription.setPurchasedByUserId(subscription.getSubscriberId());
        subscription.setStatus(status);
        subscription.setBillingInterval(license.getBillingInterval());
        subscription.setListPriceMinor(license.getPriceMinor());
        subscription.setPriceMinor(license.getPriceMinor());
        subscription.setCurrentPeriodStart(Instant.parse("2026-11-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2026-12-01T00:00:00Z"));
        return subscription;
    }
}
