package com.triphuddle.api.payment.dto;


import com.triphuddle.amqp.license.LicenseTarget;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An invoice of a license subscription; its refunds are only listed when one invoice is read.
 *
 * @param id the ID of the invoice
 * @param number the number shown to the customer, or null
 * @param licenseSubscriptionId the subscription in api-license it bills
 * @param licenseId the license
 * @param subscriberType USER or GROUP
 * @param subscriberId the user or group
 * @param billingReason why Stripe made it: {@code subscription_create}, {@code subscription_cycle} or
 *                      {@code subscription_update}
 * @param status Stripe's status: {@code open}, {@code paid}, {@code void} or {@code uncollectible}
 * @param currency the ISO 4217 currency code
 * @param totalMinor its total, tax included
 * @param taxMinor the tax in it
 * @param amountPaidMinor what was paid
 * @param amountRefundedMinor what was given back, or is on its way back
 * @param hostedInvoiceUrl Stripe's page for it, where an open one can be paid
 * @param invoicePdfUrl its PDF
 * @param createdAt when Stripe made it
 * @param paidAt when it was paid, or null
 * @param refunds its refunds, oldest first
 */
public record InvoiceResponse(
        UUID id,
        String number,
        UUID licenseSubscriptionId,
        UUID licenseId,
        LicenseTarget subscriberType,
        UUID subscriberId,
        String billingReason,
        String status,
        String currency,
        long totalMinor,
        long taxMinor,
        long amountPaidMinor,
        long amountRefundedMinor,
        String hostedInvoiceUrl,
        String invoicePdfUrl,
        Instant createdAt,
        Instant paidAt,
        List<RefundResponse> refunds
) {
}
