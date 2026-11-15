package com.triphuddle.api.payment.entity;

/**
 * Where the billing side of a license subscription is: waiting for checkout, paid and running, or over.
 */
public enum BillingSubscriptionStatus {
    PENDING,
    ACTIVE,
    ENDED
}
