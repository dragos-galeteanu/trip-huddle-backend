package com.triphuddle.amqp.license;

import java.util.UUID;

/**
 * Published when a canceled subscription is set to renew again before its period ended.
 *
 * @param subscriptionId the ID of the subscription
 * @param actorId who resumed it
 */
public record LicenseSubscriptionResumedEvent(
        UUID subscriptionId,
        UUID actorId
) {
}
