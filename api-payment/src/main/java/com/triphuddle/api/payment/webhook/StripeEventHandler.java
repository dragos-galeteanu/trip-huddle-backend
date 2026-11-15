package com.triphuddle.api.payment.webhook;

import com.stripe.model.Event;

import java.util.Set;

/**
 * Handles the Stripe events of some types; each type has at most one handler.
 * Stripe neither orders nor deduplicates deliveries, so a handler must cope with both.
 */
public interface StripeEventHandler {

    /**
     * The Stripe event types handled, e.g. {@code invoice.paid}.
     *
     * @return the types
     */
    Set<String> eventTypes();

    /**
     * Handle an event, in the transaction that marks it processed; throwing retries it later.
     *
     * @param event the event, in the API version stripe-java's models are made for
     */
    void handle(Event event);
}
