package com.triphuddle.api.license.service;


import com.triphuddle.amqp.license.LicenseTarget;
import java.util.UUID;

/**
 * Who a subscription is for: a user or a group, by ID.
 *
 * @param type whether it is a user or a group
 * @param id the ID of the user or group
 */
public record Subscriber(LicenseTarget type, UUID id) {

    /**
     * The subscriber that is a user.
     *
     * @param userId the ID of the user
     * @return the subscriber
     */
    public static Subscriber user(UUID userId) {
        return new Subscriber(LicenseTarget.USER, userId);
    }
}
