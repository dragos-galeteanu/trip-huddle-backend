package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a subscription starts: activated after payment, or granted by staff.
 *
 * @param subscriptionId the ID of the subscription
 * @param licenseId the ID of the license
 * @param licenseCode the code of the license
 * @param licenseName the name of the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the ID of the user or group
 * @param notifyUserId who to tell: the user, or for a group whoever bought it
 * @param currentPeriodEnd when the first period ends
 * @param isAutoRenew whether it renews at the end of the period
 * @param isGranted whether staff granted it for free
 * @param priceMinor what the first period costs, in minor units
 * @param currency the ISO 4217 code of the currency
 * @param entitlements what the license grants, by key; a null value is a flag
 */
public record LicenseSubscriptionActivatedEvent(
        UUID subscriptionId,
        UUID licenseId,
        String licenseCode,
        String licenseName,
        LicenseTarget subscriberType,
        UUID subscriberId,
        UUID notifyUserId,
        Instant currentPeriodEnd,
        boolean isAutoRenew,
        boolean isGranted,
        long priceMinor,
        String currency,
        Map<String, String> entitlements
) {
}
