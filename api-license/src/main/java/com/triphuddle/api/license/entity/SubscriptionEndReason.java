package com.triphuddle.api.license.entity;

/**
 * Why a subscription was ENDED rather than left to expire.
 */
public enum SubscriptionEndReason {
    SWITCHED,
    PENDING_CANCELED,
    PENDING_TIMEOUT
}
