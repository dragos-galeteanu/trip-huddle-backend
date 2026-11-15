package com.triphuddle.stripe.subscription;

import java.time.Instant;
import java.util.Map;

/**
 * A Stripe Subscription's status and current billing period.
 *
 * @param id the Stripe subscription ID
 * @param status Stripe's status, e.g. {@code active}, {@code past_due}, {@code canceled}
 * @param periodStart when the current period started
 * @param periodEnd when the current period ends
 * @param isCancelAtPeriodEnd whether it stops at the end of the period
 * @param metadata its key-values
 */
public record StripeSubscriptionPeriod(
        String id,
        String status,
        Instant periodStart,
        Instant periodEnd,
        boolean isCancelAtPeriodEnd,
        Map<String, String> metadata
) {
}
