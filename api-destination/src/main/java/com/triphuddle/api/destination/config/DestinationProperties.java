package com.triphuddle.api.destination.config;

import com.triphuddle.common.config.StorageBucketProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.destination}; blank or non-positive values fall back to defaults.
 *
 * @param accessEntitlement the license entitlement key that opens the service
 * @param accessGraceHours how long access lasts past a subscription's period end, for a renewal on its way
 * @param maxRadiusKm the largest radius a nearby search may ask for
 * @param photos the bucket photos are copied to; without one, photos are shown from Wikimedia Commons
 */
@ConfigurationProperties(prefix = "app.destination")
public record DestinationProperties(String accessEntitlement, int accessGraceHours, int maxRadiusKm,
                                    StorageBucketProperties photos) {

    public DestinationProperties {
        accessEntitlement = accessEntitlement == null || accessEntitlement.isBlank()
                ? "DESTINATIONS" : accessEntitlement.strip();
        accessGraceHours = accessGraceHours < 0 ? 48 : accessGraceHours;
        maxRadiusKm = maxRadiusKm <= 0 ? 500 : maxRadiusKm;
        photos = photos == null ? new StorageBucketProperties(null, null, 0, 0, 60) : photos;
    }
}
