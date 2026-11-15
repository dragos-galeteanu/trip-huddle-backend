package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.BillingInterval;
import com.triphuddle.api.license.entity.LicenseTarget;
import com.triphuddle.api.license.entity.SubscriptionEndReason;
import com.triphuddle.api.license.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body describing a subscription.
 *
 * @param id the ID of the subscription
 * @param licenseId the ID of the license
 * @param licenseCode the code of the license
 * @param licenseName the name of the license
 * @param subscriberType whether the subscriber is a user or a group
 * @param subscriberId the ID of the user or group
 * @param purchasedByUserId who subscribed, or who granted it
 * @param status where the subscription is
 * @param currency the ISO 4217 code of the currency
 * @param billingInterval how long one period lasts
 * @param listPriceMinor the price of a period without a promotion, as promised at subscribing
 * @param priceMinor what the current period costs
 * @param promotion the promotion it redeemed, or null if none
 * @param promotionPeriodsRemaining promoted periods left, the current one included; null with a promotion
 *                                  means every period
 * @param currentPeriodStart when the current period started, or null if it never started
 * @param currentPeriodEnd when the current period ends, or null if it never started
 * @param isAutoRenew whether it renews at the end of the period
 * @param renewalCount how many times it renewed
 * @param canceledAt when auto-renew was turned off, or null
 * @param activatedAt when it started, or null if it never did
 * @param endedAt when it expired or was ended, or null
 * @param endReason why it was ended, or null
 * @param switchedToSubscriptionId the subscription that replaced it, or null
 * @param isGranted whether staff granted it for free
 * @param grantedByUserId who granted it, or null
 * @param entitlements what the license grants now
 * @param createdAt when the subscription was created
 * @param updatedAt when the subscription was last updated
 */
public record SubscriptionResponse(
        UUID id,
        UUID licenseId,
        String licenseCode,
        String licenseName,
        LicenseTarget subscriberType,
        UUID subscriberId,
        UUID purchasedByUserId,
        SubscriptionStatus status,
        String currency,
        BillingInterval billingInterval,
        long listPriceMinor,
        long priceMinor,
        PromotionSummary promotion,
        Integer promotionPeriodsRemaining,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean isAutoRenew,
        int renewalCount,
        Instant canceledAt,
        Instant activatedAt,
        Instant endedAt,
        SubscriptionEndReason endReason,
        UUID switchedToSubscriptionId,
        boolean isGranted,
        UUID grantedByUserId,
        List<EntitlementResponse> entitlements,
        Instant createdAt,
        Instant updatedAt
) {
}
