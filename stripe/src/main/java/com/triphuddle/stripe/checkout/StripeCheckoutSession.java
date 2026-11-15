package com.triphuddle.stripe.checkout;

import java.time.Instant;

/**
 * A created Checkout Session.
 *
 * @param id the Stripe session ID
 * @param url the page to send the customer to
 * @param expiresAt when it stops taking payment
 */
public record StripeCheckoutSession(String id, String url, Instant expiresAt) {
}
