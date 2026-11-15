package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Published a few days before an auto-renewing subscription renews.
 *
 * @param subscriptionId the ID of the subscription
 * @param licenseId the ID of the license
 * @param licenseName the name of the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the ID of the user or group
 * @param notifyUserId who to tell: the user, or for a group whoever bought it
 * @param renewsAt when it renews
 * @param priceMinor what the next period costs, in minor units
 * @param currency the ISO 4217 code of the currency
 */
public record LicenseSubscriptionRenewalDueEvent(
        UUID subscriptionId,
        UUID licenseId,
        String licenseName,
        LicenseTarget subscriberType,
        UUID subscriberId,
        UUID notifyUserId,
        Instant renewsAt,
        long priceMinor,
        String currency
) {
}
