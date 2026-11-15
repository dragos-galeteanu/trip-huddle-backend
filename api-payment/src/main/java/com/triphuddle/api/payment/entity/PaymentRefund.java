package com.triphuddle.api.payment.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Money given back from an invoice's payment: by staff through the API, or in the Stripe dashboard.
 */
@Entity
@Table(
        name = "payment_refunds",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_refund_stripe_refund", columnNames = "stripe_refund_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PaymentRefund extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private PaymentInvoice invoice;

    @Column(name = "stripe_refund_id", nullable = false)
    private String stripeRefundId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Stripe's status: {@code pending}, {@code requires_action}, {@code succeeded}, {@code failed} or
     * {@code canceled}.
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "reason", length = 64)
    private String reason;

    // Null for a refund made in the Stripe dashboard.
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "stripe_created_at", nullable = false)
    private Instant stripeCreatedAt;

    /**
     * Check if the refund counts against the invoice: given back, or on its way.
     *
     * @return true unless it failed or was canceled
     */
    public boolean isCounted() {
        return !"failed".equals(status) && !"canceled".equals(status);
    }
}
