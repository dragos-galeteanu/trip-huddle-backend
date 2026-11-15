package com.triphuddle.amqp.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when charging a subscription's next period failed; Stripe retries, and the subscription keeps its
 * access until Stripe gives up and ends it.
 *
 * @param licenseSubscriptionId the ID of the subscription in api-license
 * @param failedAt when the charge failed
 */
public record PaymentSubscriptionPastDueEvent(
        UUID licenseSubscriptionId,
        Instant failedAt
) {
}
