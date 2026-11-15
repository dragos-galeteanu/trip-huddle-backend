package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an active subscription stops renewing; it still runs to the end of its period.
 *
 * @param subscriptionId the ID of the subscription
 * @param licenseId the ID of the license
 * @param licenseName the name of the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the ID of the user or group
 * @param notifyUserId who to tell: the user, or for a group whoever bought it
 * @param actorId who canceled it, or null when its group was disabled or deleted
 * @param endsAt when access ends
 */
public record LicenseSubscriptionCanceledEvent(
        UUID subscriptionId,
        UUID licenseId,
        String licenseName,
        String subscriberType,
        UUID subscriberId,
        UUID notifyUserId,
        UUID actorId,
        Instant endsAt
) {
}
