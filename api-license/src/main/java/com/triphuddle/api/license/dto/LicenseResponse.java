package com.triphuddle.api.license.dto;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.entity.LicenseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body describing a license.
 *
 * @param id the ID of the license
 * @param code the unique code of the license
 * @param name the name of the license
 * @param description the description of the license
 * @param target whether the license is bought for a user or a group
 * @param status where the license is in its lifecycle
 * @param priceMinor the price of one period, in minor units of the currency
 * @param currency the ISO 4217 code of the currency
 * @param billingInterval how long one period lasts
 * @param entitlements what the license grants
 * @param promotion the automatic promotion running now, or null if there is none
 * @param promotedPriceMinor the price with that promotion, or null if there is none
 * @param publishedAt when the license was last published, or null if it never was
 * @param archivedAt when the license was archived, or null if it is not
 * @param createdAt when the license was created
 * @param updatedAt when the license was last updated
 */
public record LicenseResponse(
        UUID id,
        String code,
        String name,
        String description,
        LicenseTarget target,
        LicenseStatus status,
        long priceMinor,
        String currency,
        BillingInterval billingInterval,
        List<EntitlementResponse> entitlements,
        PromotionSummary promotion,
        Long promotedPriceMinor,
        Instant publishedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
