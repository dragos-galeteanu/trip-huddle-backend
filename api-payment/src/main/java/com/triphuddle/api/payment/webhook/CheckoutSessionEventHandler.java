package com.triphuddle.api.payment.webhook;

import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.triphuddle.api.payment.service.BillingSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Handles Checkout Sessions: a paid one activates its subscription, an expired one is forgotten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutSessionEventHandler implements StripeEventHandler {

    static final String COMPLETED = "checkout.session.completed";
    static final String ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    static final String EXPIRED = "checkout.session.expired";

    private static final Set<String> PAID_STATUSES = Set.of("paid", "no_payment_required");

    private final BillingSubscriptionService billingSubscriptionService;

    @Override
    public Set<String> eventTypes() {
        return Set.of(COMPLETED, ASYNC_PAYMENT_SUCCEEDED, EXPIRED);
    }

    @Override
    public void handle(Event event) {
        Session session = (Session) StripeEvents.objectOf(event);
        if (!"subscription".equals(session.getMode()) || session.getClientReferenceId() == null) {
            log.info("Checkout session not ours skipped [sessionId={}]", session.getId());
            return;
        }
        UUID licenseSubscriptionId = UUID.fromString(session.getClientReferenceId());
        if (EXPIRED.equals(event.getType())) {
            billingSubscriptionService.onCheckoutExpired(licenseSubscriptionId, session.getId());
            return;
        }
        // A completed session whose payment is still on its way is finished by async_payment_succeeded.
        if (!PAID_STATUSES.contains(session.getPaymentStatus())) {
            log.info("Checkout completed, payment pending [licenseSubscriptionId={}, sessionId={}]",
                    licenseSubscriptionId, session.getId());
            return;
        }
        billingSubscriptionService.onCheckoutPaid(licenseSubscriptionId, session.getId(), session.getCustomer(),
                session.getSubscription());
    }
}
