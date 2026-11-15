package com.triphuddle.stripe.checkout;

/**
 * Where a Checkout Session is, as Stripe reports it now.
 *
 * @param id the Stripe session ID
 * @param status {@code open}, {@code complete} or {@code expired}
 * @param paymentStatus {@code paid}, {@code unpaid} or {@code no_payment_required}
 * @param customerId the Stripe customer, or null
 * @param subscriptionId the Stripe subscription it created, or null
 */
public record StripeCheckoutSessionState(
        String id,
        String status,
        String paymentStatus,
        String customerId,
        String subscriptionId
) {
}
