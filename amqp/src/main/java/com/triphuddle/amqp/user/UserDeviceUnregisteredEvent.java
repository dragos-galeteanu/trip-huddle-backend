package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user stops push notifications on a device, typically on sign-out.
 *
 * @param userId the ID of the user
 * @param token the Firebase Cloud Messaging registration token of the device
 */
public record UserDeviceUnregisteredEvent(UUID userId, String token) {
}
