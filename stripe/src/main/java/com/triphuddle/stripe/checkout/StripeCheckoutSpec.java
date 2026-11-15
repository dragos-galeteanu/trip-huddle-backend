package com.triphuddle.stripe.checkout;

import java.time.Instant;
import java.util.Map;

/**
 * A Checkout Session that starts a subscription to one price.
 *
 * @param customerId the Stripe customer who pays
 * @param priceId the recurring Stripe price
 * @param couponId a coupon to apply, or null
 * @param successUrl where Stripe sends the customer after paying
 * @param cancelUrl where Stripe sends the customer who goes back
 * @param expiresAt when the session stops taking payment; Stripe allows 30 minutes to 24 hours from now
 * @param clientReferenceId our reference, shown in the dashboard and sent back in events
 * @param metadata key-values stored on the session and on the subscription it creates
 */
public record StripeCheckoutSpec(
        String customerId,
        String priceId,
        String couponId,
        String successUrl,
        String cancelUrl,
        Instant expiresAt,
        String clientReferenceId,
        Map<String, String> metadata
) {
}
