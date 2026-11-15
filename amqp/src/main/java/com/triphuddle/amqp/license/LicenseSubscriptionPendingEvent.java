package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a subscription is created and waits for payment; it carries what the checkout must charge.
 *
 * @param subscriptionId the ID of the subscription
 * @param licenseId the ID of the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the ID of the user or group
 * @param purchasedByUserId the user who subscribed
 * @param billingInterval MONTH or YEAR
 * @param listPriceMinor the price of a period without promotion, in minor units
 * @param priceMinor the price of the first period, with any promotion
 * @param currency the ISO 4217 currency code
 * @param promotionPeriods how many periods the promotion covers, or null for all of them; ignored without one
 * @param createdAt when it was created
 * @param expiresAt when it ends unless paid
 */
public record LicenseSubscriptionPendingEvent(
        UUID subscriptionId,
        UUID licenseId,
        LicenseTarget subscriberType,
        UUID subscriberId,
        UUID purchasedByUserId,
        BillingInterval billingInterval,
        long listPriceMinor,
        long priceMinor,
        String currency,
        Integer promotionPeriods,
        Instant createdAt,
        Instant expiresAt
) {
}
