package com.triphuddle.stripe.catalog;

import java.util.Map;

/**
 * A recurring, tax-inclusive Stripe Price, found again by its lookup key.
 *
 * @param productId the Stripe product ID
 * @param lookupKey a key unique to this product, amount, currency and interval
 * @param amountMinor the amount per period, in minor units
 * @param currency the ISO 4217 currency code
 * @param isYearly true for a yearly price, false for a monthly one
 * @param metadata extra key-values to store on it
 */
public record StripePriceSpec(
        String productId,
        String lookupKey,
        long amountMinor,
        String currency,
        boolean isYearly,
        Map<String, String> metadata
) {
}
