package com.triphuddle.api.payment.webhook;

import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.triphuddle.api.payment.service.StripeBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Handles Stripe subscriptions that Stripe ended, or whose renewal was changed in Stripe.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionEventHandler implements StripeEventHandler {

    static final String DELETED = "customer.subscription.deleted";
    static final String UPDATED = "customer.subscription.updated";

    private final StripeBillingService stripeBillingService;

    @Override
    public Set<String> eventTypes() {
        return Set.of(DELETED, UPDATED);
    }

    @Override
    public void handle(Event event) {
        Subscription subscription = (Subscription) StripeEvents.objectOf(event);
        if (DELETED.equals(event.getType())) {
            Instant endedAt = subscription.getEndedAt() == null
                    ? Instant.ofEpochSecond(event.getCreated()) : Instant.ofEpochSecond(subscription.getEndedAt());
            String reason = subscription.getCancellationDetails() == null
                    ? null : subscription.getCancellationDetails().getReason();
            stripeBillingService.onStripeSubscriptionDeleted(subscription.getId(), endedAt, reason);
        } else {
            stripeBillingService.onStripeSubscriptionUpdated(subscription.getId(),
                    Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        }
    }
}
