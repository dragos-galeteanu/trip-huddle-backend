package com.triphuddle.api.payment.dto;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The billing side of a license subscription, for staff.
 *
 * @param licenseSubscriptionId the subscription in api-license
 * @param status PENDING, ACTIVE or ENDED
 * @param licenseId the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the user or group
 * @param purchasedByUserId who subscribed
 * @param billingInterval MONTH or YEAR
 * @param listPriceMinor the price of a period without promotion
 * @param firstPriceMinor the price of the first period
 * @param currency the ISO 4217 currency code
 * @param stripeCustomerId the Stripe customer, or null
 * @param stripeSubscriptionId the Stripe subscription that bills it now, or null
 * @param isCancelAtPeriodEnd whether Stripe stops it at the end of the period
 * @param paidAt when its first payment came in, or null
 * @param activatedAt when it was activated, or null
 * @param endedAt when it ended, or null
 * @param refundDueAt when a payment came in that it couldn't take, while that waits for a refund; or null
 */
public record BillingSubscriptionResponse(
        UUID licenseSubscriptionId,
        BillingSubscriptionStatus status,
        UUID licenseId,
        LicenseTarget subscriberType,
        UUID subscriberId,
        UUID purchasedByUserId,
        BillingInterval billingInterval,
        Long listPriceMinor,
        Long firstPriceMinor,
        String currency,
        String stripeCustomerId,
        String stripeSubscriptionId,
        boolean isCancelAtPeriodEnd,
        Instant paidAt,
        Instant activatedAt,
        Instant endedAt,
        Instant refundDueAt
) {
}
