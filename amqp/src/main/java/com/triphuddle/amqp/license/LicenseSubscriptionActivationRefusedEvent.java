package com.triphuddle.amqp.license;

import java.util.UUID;

/**
 * Published in reply to a payment for a subscription that no longer waits for it, e.g. one that timed out or
 * that staff activated, so that api-payment stops the Stripe subscription.
 *
 * @param subscriptionId the ID of the subscription
 * @param stripeSubscriptionId the Stripe subscription that was paid
 * @param status the subscription's status when the payment arrived
 */
public record LicenseSubscriptionActivationRefusedEvent(
        UUID subscriptionId,
        String stripeSubscriptionId,
        String status
) {
}
