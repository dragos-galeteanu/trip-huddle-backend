package com.triphuddle.api.payment.webhook;

import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.triphuddle.api.payment.service.InvoiceService;
import com.triphuddle.stripe.refund.StripeRefundClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Handles refunds, whoever made them, so those made in the Stripe dashboard are recorded too.
 */
@Component
@RequiredArgsConstructor
public class RefundEventHandler implements StripeEventHandler {

    static final String CREATED = "refund.created";
    static final String UPDATED = "refund.updated";
    static final String FAILED = "refund.failed";

    private final InvoiceService invoiceService;

    @Override
    public Set<String> eventTypes() {
        return Set.of(CREATED, UPDATED, FAILED);
    }

    @Override
    public void handle(Event event) {
        invoiceService.onRefund(StripeRefundClient.of((Refund) StripeEvents.objectOf(event)));
    }
}
