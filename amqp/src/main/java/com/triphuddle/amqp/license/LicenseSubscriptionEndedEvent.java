package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a subscription is over: EXPIRED at the end of its period, or ENDED early.
 *
 * @param subscriptionId the ID of the subscription
 * @param licenseId the ID of the license
 * @param licenseName the name of the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the ID of the user or group
 * @param notifyUserId who to tell: the user, or for a group whoever bought it
 * @param status EXPIRED or ENDED
 * @param endReason why it was ENDED: SWITCHED, PENDING_CANCELED or PENDING_TIMEOUT; null if it EXPIRED
 * @param endedAt when it ended
 */
public record LicenseSubscriptionEndedEvent(
        UUID subscriptionId,
        UUID licenseId,
        String licenseName,
        LicenseTarget subscriberType,
        UUID subscriberId,
        UUID notifyUserId,
        String status,
        String endReason,
        Instant endedAt
) {
}
