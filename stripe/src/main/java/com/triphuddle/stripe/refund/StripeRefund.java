package com.triphuddle.stripe.refund;

import java.time.Instant;
import java.util.Map;

/**
 * A Stripe refund.
 *
 * @param id the Stripe refund ID
 * @param paymentIntentId the payment intent it gives money back from
 * @param amountMinor the amount, in minor units
 * @param currency the ISO 4217 currency code
 * @param status Stripe's status: {@code pending}, {@code requires_action}, {@code succeeded}, {@code failed} or
 *               {@code canceled}
 * @param reason Stripe's reason, or null
 * @param createdAt when it was made
 * @param metadata its key-values
 */
public record StripeRefund(
        String id,
        String paymentIntentId,
        long amountMinor,
        String currency,
        String status,
        String reason,
        Instant createdAt,
        Map<String, String> metadata
) {
}
