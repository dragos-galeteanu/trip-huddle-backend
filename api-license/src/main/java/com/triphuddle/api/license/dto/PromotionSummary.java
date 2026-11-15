package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.DiscountType;

import java.time.Instant;
import java.util.UUID;

/**
 * The terms of a promotion as customers see them: no redemption counts and no codes they did not enter.
 *
 * @param id the ID of the promotion
 * @param name the name of the promotion
 * @param code the code the customer entered, or null for an automatic promotion
 * @param discountType whether the value is an amount or a percentage
 * @param discountValue minor units off, or the percentage off
 * @param durationPeriods how many billing periods are discounted, or null for every period
 * @param startsAt when the promotion starts
 * @param endsAt when the promotion ends, or null if it runs until further notice
 * @param isOncePerCustomer whether a customer can use it only once
 * @param isNewCustomersOnly whether only customers who never had a subscription can use it
 */
public record PromotionSummary(
        UUID id,
        String name,
        String code,
        DiscountType discountType,
        long discountValue,
        Integer durationPeriods,
        Instant startsAt,
        Instant endsAt,
        boolean isOncePerCustomer,
        boolean isNewCustomersOnly
) {
}
