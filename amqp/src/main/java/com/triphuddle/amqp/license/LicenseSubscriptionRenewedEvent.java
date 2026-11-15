package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a subscription moves on to its next period.
 *
 * @param subscriptionId the ID of the subscription
 * @param licenseId the ID of the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the ID of the user or group
 * @param currentPeriodEnd when the new period ends
 * @param priceMinor what the new period costs, in minor units
 */
public record LicenseSubscriptionRenewedEvent(
        UUID subscriptionId,
        UUID licenseId,
        LicenseTarget subscriberType,
        UUID subscriberId,
        Instant currentPeriodEnd,
        long priceMinor
) {
}
