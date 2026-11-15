package com.triphuddle.api.payment.entity;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A Stripe invoice of a license subscription, as the webhook last reported it, with what was refunded from it.
 */
@Entity
@Table(
        name = "payment_invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_invoice_stripe_invoice", columnNames = "stripe_invoice_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PaymentInvoice extends BaseJpaEntity {

    @Column(name = "stripe_invoice_id", nullable = false)
    private String stripeInvoiceId;

    @Column(name = "number", length = 64)
    private String number;

    @Column(name = "license_subscription_id", nullable = false)
    private UUID licenseSubscriptionId;

    @Column(name = "license_id")
    private UUID licenseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", nullable = false, length = 16)
    private LicenseTarget subscriberType;

    @Column(name = "subscriber_id", nullable = false)
    private UUID subscriberId;

    @Column(name = "stripe_subscription_id", nullable = false)
    private String stripeSubscriptionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    /**
     * Why Stripe made it: {@code subscription_create}, {@code subscription_cycle} or {@code subscription_update}.
     */
    @Column(name = "billing_reason", length = 32)
    private String billingReason;

    /**
     * Stripe's status: {@code open}, {@code paid}, {@code void} or {@code uncollectible}.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "amount_paid_minor", nullable = false)
    private long amountPaidMinor;

    @Column(name = "amount_refunded_minor", nullable = false)
    private long amountRefundedMinor;

    @Column(name = "hosted_invoice_url", columnDefinition = "TEXT")
    private String hostedInvoiceUrl;

    @Column(name = "invoice_pdf_url", columnDefinition = "TEXT")
    private String invoicePdfUrl;

    @Column(name = "stripe_created_at", nullable = false)
    private Instant stripeCreatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * What can still be refunded.
     *
     * @return the paid amount less what was refunded, never below zero
     */
    public long refundableMinor() {
        return Math.max(amountPaidMinor - amountRefundedMinor, 0);
    }
}
