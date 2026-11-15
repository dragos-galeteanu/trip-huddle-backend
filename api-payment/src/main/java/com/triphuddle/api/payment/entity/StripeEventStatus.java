package com.triphuddle.api.payment.entity;

/**
 * Where a received Stripe event is: waiting to be handled, handled, of a type nobody handles, or given up on.
 */
public enum StripeEventStatus {
    PENDING,
    PROCESSED,
    IGNORED,
    FAILED
}
