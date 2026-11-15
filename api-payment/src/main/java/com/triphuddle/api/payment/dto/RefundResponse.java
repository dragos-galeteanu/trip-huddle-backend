package com.triphuddle.api.payment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A refund of an invoice.
 *
 * @param id the ID of the refund
 * @param stripeRefundId the Stripe refund ID
 * @param amountMinor the amount, in minor units
 * @param currency the ISO 4217 currency code
 * @param status Stripe's status: {@code pending}, {@code requires_action}, {@code succeeded}, {@code failed} or
 *               {@code canceled}
 * @param reason Stripe's reason, or null
 * @param createdByUserId the staff member who made it, or null if made in the Stripe dashboard
 * @param createdAt when it was made
 */
public record RefundResponse(
        UUID id,
        String stripeRefundId,
        long amountMinor,
        String currency,
        String status,
        String reason,
        UUID createdByUserId,
        Instant createdAt
) {
}
