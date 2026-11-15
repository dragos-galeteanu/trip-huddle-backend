package com.triphuddle.api.payment.service;

import lombok.Getter;

import java.util.UUID;

/**
 * Carries the ID of the subscription whose comparison with Stripe failed, out of the transaction that rolled back.
 */
@Getter
public class ReconciliationException extends RuntimeException {

    private final UUID licenseSubscriptionId;

    public ReconciliationException(UUID licenseSubscriptionId, Throwable cause) {
        super("Reconciling subscription id=%s failed".formatted(licenseSubscriptionId), cause);
        this.licenseSubscriptionId = licenseSubscriptionId;
    }
}
