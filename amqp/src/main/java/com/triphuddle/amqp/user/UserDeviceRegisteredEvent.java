package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user registers a device for push notifications.
 *
 * @param userId the ID of the user
 * @param token the Firebase Cloud Messaging registration token of the device
 * @param platform the platform of the device: ANDROID, IOS or WEB
 */
public record UserDeviceRegisteredEvent(UUID userId, String token, String platform) {
}
