package com.triphuddle.amqp.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a license that left draft is published, archived or has its details changed; carries its whole
 * state, so a consumer can apply the latest one it sees and drop older ones.
 *
 * @param licenseId the ID of the license
 * @param code the license code
 * @param name the license name
 * @param description the description, or null
 * @param target USER or GROUP
 * @param status PUBLISHED or ARCHIVED
 * @param priceMinor the current price for new subscribers, in minor units
 * @param currency the ISO 4217 currency code
 * @param billingInterval MONTH or YEAR
 * @param changedAt when the change happened, to order events
 */
public record LicenseCatalogChangedEvent(
        UUID licenseId,
        String code,
        String name,
        String description,
        LicenseTarget target,
        String status,
        long priceMinor,
        String currency,
        BillingInterval billingInterval,
        Instant changedAt
) {
}
