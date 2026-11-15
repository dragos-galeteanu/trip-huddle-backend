package com.triphuddle.api.payment.dto;

import com.triphuddle.api.payment.entity.StripeEventStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored Stripe event; the raw payload is only set when one event is read.
 */
public record StripeEventResponse(
        UUID id,
        String stripeEventId,
        String eventType,
        String apiVersion,
        boolean isLivemode,
        Instant stripeCreatedAt,
        Instant receivedAt,
        StripeEventStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant processedAt,
        String lastError,
        String payload
) {
}
