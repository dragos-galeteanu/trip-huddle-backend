package com.triphuddle.amqp.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when Stripe charged a subscription's next period, on the first try or a later one.
 *
 * @param licenseSubscriptionId the ID of the subscription in api-license
 * @param stripeSubscriptionId the Stripe subscription that bills it
 * @param periodStart when the paid period started
 * @param periodEnd when the paid period ends
 */
public record PaymentSubscriptionRenewedEvent(
        UUID licenseSubscriptionId,
        String stripeSubscriptionId,
        Instant periodStart,
        Instant periodEnd
) {
}
