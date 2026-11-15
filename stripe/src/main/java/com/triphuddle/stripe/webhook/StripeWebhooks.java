package com.triphuddle.stripe.webhook;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.triphuddle.stripe.config.StripeProperties;
import lombok.RequiredArgsConstructor;

/**
 * Verifies webhook signatures and parses stored events back into Stripe's models.
 */
@RequiredArgsConstructor
public class StripeWebhooks {

    private final StripeClient stripeClient;
    private final StripeProperties properties;

    /**
     * Verify a webhook request and parse its event.
     *
     * @param payload the raw request body, exactly as received
     * @param signatureHeader the {@code Stripe-Signature} header
     * @return the event
     * @throws StripeSignatureException if the signature is missing, wrong or too old
     */
    public Event verify(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new StripeSignatureException("Missing Stripe-Signature header", null);
        }
        try {
            return stripeClient.constructEvent(payload, signatureHeader, properties.webhookSecret(),
                    properties.webhookToleranceSeconds());
        } catch (SignatureVerificationException e) {
            throw new StripeSignatureException("Invalid Stripe-Signature header", e);
        }
    }

    /**
     * Parse an event that was verified when it was received.
     *
     * @param payload the stored raw body
     * @return the event
     */
    public Event parse(String payload) {
        return stripeClient.constructEventWithoutVerification(payload);
    }

    /**
     * Check if an event's object was rendered in the API version stripe-java's models are made for.
     *
     * @param event the event
     * @return true if its data can be read into Stripe's models
     */
    public boolean isReadable(Event event) {
        return Stripe.API_VERSION.equals(event.getApiVersion());
    }
}
