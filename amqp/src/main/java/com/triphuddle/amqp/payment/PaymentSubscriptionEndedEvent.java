package com.triphuddle.amqp.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when Stripe ended a subscription: at the end of a canceled period, or after its last failed retry.
 *
 * @param licenseSubscriptionId the ID of the subscription in api-license
 * @param stripeSubscriptionId the Stripe subscription that billed it
 * @param endedAt when it ended
 * @param reason Stripe's reason, e.g. {@code cancellation_requested} or {@code payment_failed}, or null
 */
public record PaymentSubscriptionEndedEvent(
        UUID licenseSubscriptionId,
        String stripeSubscriptionId,
        Instant endedAt,
        String reason
) {
}
