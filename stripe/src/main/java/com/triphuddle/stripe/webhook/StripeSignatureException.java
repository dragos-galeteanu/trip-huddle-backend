package com.triphuddle.stripe.webhook;

/**
 * Thrown when a webhook request is not signed by Stripe with the endpoint's secret, or is too old.
 */
public class StripeSignatureException extends RuntimeException {

    public StripeSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
