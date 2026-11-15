package com.triphuddle.api.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Which subscription waiting for payment to pay for.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    /**
     * The ID of the subscription in api-license.
     */
    @NotNull
    private UUID subscriptionId;
}
