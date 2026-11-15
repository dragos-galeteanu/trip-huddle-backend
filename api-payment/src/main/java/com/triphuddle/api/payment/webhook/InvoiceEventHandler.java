package com.triphuddle.api.payment.webhook;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.triphuddle.api.payment.service.StripeBillingService;
import com.triphuddle.api.payment.service.StripeInvoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Handles subscription invoices: each is recorded; paid ones renew and are emailed, failed renewals mark the
 * subscription past due.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceEventHandler implements StripeEventHandler {

    static final String PAID = "invoice.paid";
    static final String PAYMENT_FAILED = "invoice.payment_failed";
    static final String VOIDED = "invoice.voided";
    static final String MARKED_UNCOLLECTIBLE = "invoice.marked_uncollectible";

    private final StripeBillingService stripeBillingService;

    @Override
    public Set<String> eventTypes() {
        return Set.of(PAID, PAYMENT_FAILED, VOIDED, MARKED_UNCOLLECTIBLE);
    }

    @Override
    public void handle(Event event) {
        Invoice invoice = (Invoice) StripeEvents.objectOf(event);
        String stripeSubscriptionId = invoice.getParent() == null
                || invoice.getParent().getSubscriptionDetails() == null
                ? null : invoice.getParent().getSubscriptionDetails().getSubscription();
        if (stripeSubscriptionId == null) {
            log.info("Invoice without a subscription skipped [invoiceId={}]", invoice.getId());
            return;
        }
        long tax = invoice.getTotalTaxes() == null ? 0 : invoice.getTotalTaxes().stream()
                .mapToLong(total -> valueOf(total.getAmount())).sum();
        Long paidAt = invoice.getStatusTransitions() == null ? null : invoice.getStatusTransitions().getPaidAt();
        StripeInvoice summary = new StripeInvoice(invoice.getId(), invoice.getNumber(), stripeSubscriptionId,
                invoice.getBillingReason(), invoice.getStatus(), valueOf(invoice.getTotal()), tax,
                valueOf(invoice.getAmountPaid()), valueOf(invoice.getAmountDue()), invoice.getCurrency(),
                invoice.getHostedInvoiceUrl(), invoice.getInvoicePdf(), instantOf(invoice.getCreated()),
                paidAt == null ? null : Instant.ofEpochSecond(paidAt), instantOf(invoice.getNextPaymentAttempt()));
        switch (event.getType()) {
            case PAID -> stripeBillingService.onInvoicePaid(summary);
            case PAYMENT_FAILED -> stripeBillingService.onInvoicePaymentFailed(summary);
            default -> stripeBillingService.onInvoiceChanged(summary);
        }
    }

    private static Instant instantOf(Long epochSecond) {
        return epochSecond == null ? null : Instant.ofEpochSecond(epochSecond);
    }

    private static long valueOf(Long amount) {
        return amount == null ? 0 : amount;
    }
}
