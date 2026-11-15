package com.triphuddle.api.payment.webhook;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;

/**
 * Reads the object of an event, which the processor already checked is in a readable API version.
 */
final class StripeEvents {

    private StripeEvents() {
    }

    static StripeObject objectOf(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException("Event %s has no readable object".formatted(
                        event.getId())));
    }
}
