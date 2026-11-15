package com.triphuddle.api.payment.service;

import java.time.Instant;

/**
 * The parts of a Stripe subscription invoice that billing needs.
 *
 * @param id the Stripe invoice ID
 * @param number the invoice number shown to the customer, or null while a draft
 * @param stripeSubscriptionId the Stripe subscription it bills
 * @param billingReason why it was made, e.g. {@code subscription_create} or {@code subscription_cycle}
 * @param status Stripe's status: {@code open}, {@code paid}, {@code void} or {@code uncollectible}
 * @param totalMinor its total, tax included
 * @param taxMinor the tax in it
 * @param amountMinor what was paid, tax included
 * @param amountDueMinor what is due, tax included
 * @param currency the ISO 4217 currency code
 * @param hostedInvoiceUrl Stripe's page for the invoice, or null
 * @param invoicePdfUrl the PDF of the invoice, or null
 * @param createdAt when Stripe made it
 * @param paidAt when it was paid, or null
 * @param nextPaymentAttemptAt when Stripe tries a failed charge again, or null
 */
public record StripeInvoice(
        String id,
        String number,
        String stripeSubscriptionId,
        String billingReason,
        String status,
        long totalMinor,
        long taxMinor,
        long amountMinor,
        long amountDueMinor,
        String currency,
        String hostedInvoiceUrl,
        String invoicePdfUrl,
        Instant createdAt,
        Instant paidAt,
        Instant nextPaymentAttemptAt
) {
}
