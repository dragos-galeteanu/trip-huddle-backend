package com.triphuddle.api.payment.service;

import lombok.Getter;

import java.util.UUID;

/**
 * Carries the ID of the stored event whose handling failed, out of the transaction that rolled back.
 */
@Getter
public class StripeEventProcessingException extends RuntimeException {

    private final UUID eventId;

    public StripeEventProcessingException(UUID eventId, Throwable cause) {
        super("Handling stored Stripe event id=%s failed".formatted(eventId), cause);
        this.eventId = eventId;
    }
}
