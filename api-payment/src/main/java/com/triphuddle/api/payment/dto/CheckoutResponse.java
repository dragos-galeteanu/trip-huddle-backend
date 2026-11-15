package com.triphuddle.api.payment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The Stripe Checkout page to send the customer to, or none when it is paid already: by an earlier checkout, or
 * by switching the subscriber's paid subscription to it, charging the difference at once.
 *
 * @param subscriptionId the ID of the subscription in api-license
 * @param checkoutUrl the Stripe-hosted page, or null when paid
 * @param expiresAt when the page stops taking payment, or null when paid
 * @param isPaid whether it is paid, with no page to visit; it activates shortly
 */
public record CheckoutResponse(UUID subscriptionId, String checkoutUrl, Instant expiresAt, boolean isPaid) {
}
