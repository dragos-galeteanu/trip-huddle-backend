package com.triphuddle.stripe.invoice;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.InvoicePayment;
import com.stripe.param.InvoicePaymentListParams;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * Reads how Stripe invoices were paid.
 */
@RequiredArgsConstructor
public class StripeInvoiceClient {

    private final StripeClient stripeClient;

    /**
     * Find the payment intent that paid an invoice, which refunds are made against.
     *
     * @param invoiceId the Stripe invoice ID
     * @return the payment intent ID, or empty if the invoice was not paid by one, e.g. a free invoice
     */
    public Optional<String> paidPaymentIntentId(String invoiceId) {
        try {
            return stripeClient.v1().invoicePayments().list(InvoicePaymentListParams.builder()
                            .setInvoice(invoiceId)
                            .setStatus(InvoicePaymentListParams.Status.PAID)
                            .setLimit(10L)
                            .build())
                    .getData().stream()
                    .map(InvoicePayment::getPayment)
                    .filter(payment -> payment != null && payment.getPaymentIntent() != null)
                    .map(InvoicePayment.Payment::getPaymentIntent)
                    .findFirst();
        } catch (StripeException e) {
            throw new StripeIntegrationException("Reading payments of Stripe invoice %s failed".formatted(invoiceId),
                    e);
        }
    }
}
