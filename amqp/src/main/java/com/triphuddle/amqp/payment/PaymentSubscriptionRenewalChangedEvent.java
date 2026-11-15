package com.triphuddle.amqp.payment;

import java.util.UUID;

/**
 * Published when a subscription was set to stop, or to keep renewing, on the Stripe side: from the dashboard,
 * or because its user was disabled or deleted.
 *
 * @param licenseSubscriptionId the ID of the subscription in api-license
 * @param isAutoRenew whether it renews at the end of its period
 */
public record PaymentSubscriptionRenewalChangedEvent(
        UUID licenseSubscriptionId,
        boolean isAutoRenew
) {
}
