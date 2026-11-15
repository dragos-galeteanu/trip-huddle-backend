package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.entity.PromotionState;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing a promotion, for staff.
 *
 * @param id the ID of the promotion
 * @param licenseId the ID of its license
 * @param name the name of the promotion
 * @param code the code that unlocks it, or null if it applies automatically
 * @param discountType whether the value is an amount or a percentage
 * @param discountValue minor units off, or the percentage off
 * @param startsAt when the promotion starts
 * @param endsAt when the promotion ends, or null if it runs until further notice
 * @param durationPeriods how many billing periods are discounted, or null for every period
 * @param maxRedemptions how many subscriptions may use it, or null for no limit
 * @param redemptionCount how many subscriptions used it
 * @param isOncePerCustomer whether a customer can use it only once
 * @param isNewCustomersOnly whether only customers who never had a subscription can use it
 * @param minPriceMinor the price it never goes below, in minor units
 * @param discountedPriceMinor the license's current price with this promotion, in minor units
 * @param state where its run stands now
 * @param createdAt when the promotion was created
 * @param updatedAt when the promotion was last updated
 */
public record PromotionResponse(
        UUID id,
        UUID licenseId,
        String name,
        String code,
        DiscountType discountType,
        long discountValue,
        Instant startsAt,
        Instant endsAt,
        Integer durationPeriods,
        Integer maxRedemptions,
        int redemptionCount,
        boolean isOncePerCustomer,
        boolean isNewCustomersOnly,
        long minPriceMinor,
        long discountedPriceMinor,
        PromotionState state,
        Instant createdAt,
        Instant updatedAt
) {
}
