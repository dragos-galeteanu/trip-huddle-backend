package com.triphuddle.api.payment.service;

import lombok.Getter;

import java.util.UUID;

/**
 * Carries the ID of the license whose Stripe sync failed, out of the transaction that rolled back.
 */
@Getter
public class CatalogSyncException extends RuntimeException {

    private final UUID licenseId;

    public CatalogSyncException(UUID licenseId, Throwable cause) {
        super("Syncing license id=%s to Stripe failed".formatted(licenseId), cause);
        this.licenseId = licenseId;
    }
}
