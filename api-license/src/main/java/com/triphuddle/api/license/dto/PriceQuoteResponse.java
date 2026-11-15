package com.triphuddle.api.license.dto;

import com.triphuddle.amqp.license.BillingInterval;

import java.util.UUID;

/**
 * What one period of a license costs now, with the promotion that would apply.
 *
 * @param licenseId the ID of the license
 * @param currency the ISO 4217 code of the currency
 * @param billingInterval how long one period lasts
 * @param listPriceMinor the price without a promotion, in minor units
 * @param priceMinor the price with the promotion, in minor units
 * @param promotion the promotion that applies, or null if none does
 */
public record PriceQuoteResponse(
        UUID licenseId,
        String currency,
        BillingInterval billingInterval,
        long listPriceMinor,
        long priceMinor,
        PromotionSummary promotion
) {
}
