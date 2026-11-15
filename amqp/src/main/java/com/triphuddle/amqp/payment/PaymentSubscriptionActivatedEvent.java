package com.triphuddle.amqp.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the first payment of a subscription went through at checkout; its first period is Stripe's.
 *
 * @param licenseSubscriptionId the ID of the subscription in api-license
 * @param stripeSubscriptionId the Stripe subscription that bills it
 * @param periodStart when the paid period started
 * @param periodEnd when the paid period ends
 */
public record PaymentSubscriptionActivatedEvent(
        UUID licenseSubscriptionId,
        String stripeSubscriptionId,
        Instant periodStart,
        Instant periodEnd
) {
}
