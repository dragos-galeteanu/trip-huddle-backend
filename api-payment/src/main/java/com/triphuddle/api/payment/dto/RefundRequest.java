package com.triphuddle.api.payment.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How much of an invoice to refund, and whether its subscription ends with it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    /**
     * The amount in minor units; null refunds all that is left.
     */
    @Positive
    private Long amountMinor;

    /**
     * Whether to stop the Stripe subscription at once, which expires the subscription.
     */
    private Boolean isEndingSubscription = false;

    /**
     * Whether the customer asked for it, which Stripe records as the reason.
     */
    private Boolean isRequestedByCustomer = false;
}
