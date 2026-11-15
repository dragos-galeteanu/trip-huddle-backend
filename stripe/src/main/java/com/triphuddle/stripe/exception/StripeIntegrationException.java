package com.triphuddle.stripe.exception;

import com.stripe.exception.StripeException;
import lombok.Getter;

/**
 * Thrown when a Stripe API call fails; carries Stripe's HTTP status and error code when it answered.
 */
@Getter
public class StripeIntegrationException extends RuntimeException {

    private final Integer statusCode;
    private final String stripeCode;

    public StripeIntegrationException(String message, StripeException cause) {
        super("%s: %s".formatted(message, cause.getMessage()), cause);
        this.statusCode = cause.getStatusCode();
        this.stripeCode = cause.getCode();
    }
}
