package com.triphuddle.api.license.entity;

/**
 * Where a subscription is: waiting for payment, running, or over by running out or by being ended.
 */
public enum SubscriptionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    EXPIRED,
    ENDED
}
