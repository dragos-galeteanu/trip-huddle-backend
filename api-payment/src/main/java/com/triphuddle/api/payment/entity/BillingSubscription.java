package com.triphuddle.api.payment.entity;

import com.triphuddle.amqp.license.BillingInterval;
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
 * The billing side of a license subscription that waits or waited for payment; granted ones never have one.
 * Its details come from api-license's pending event; an end that arrives first leaves only the ID and status.
 */
@Entity
@Table(
        name = "payment_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_billing_subscription_license_subscription", columnNames = "license_subscription_id"),
                @UniqueConstraint(
                        name = "uk_billing_subscription_stripe_subscription", columnNames = "stripe_subscription_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BillingSubscription extends BaseJpaEntity {

    @Column(name = "license_subscription_id", nullable = false)
    private UUID licenseSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BillingSubscriptionStatus status;

    @Column(name = "license_id")
    private UUID licenseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", length = 16)
    private LicenseTarget subscriberType;

    @Column(name = "subscriber_id")
    private UUID subscriberId;

    @Column(name = "purchased_by_user_id")
    private UUID purchasedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 16)
    private BillingInterval billingInterval;

    @Column(name = "list_price_minor")
    private Long listPriceMinor;

    @Column(name = "first_price_minor")
    private Long firstPriceMinor;

    @Column(name = "currency", length = 3)
    private String currency;

    // How many periods are discounted, or null for all of them; unused when the prices are equal.
    @Column(name = "promotion_periods")
    private Integer promotionPeriods;

    @Column(name = "pending_created_at")
    private Instant pendingCreatedAt;

    @Column(name = "pending_expires_at")
    private Instant pendingExpiresAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /**
     * EXPIRED or ENDED, as api-license reported it.
     */
    @Column(name = "license_end_status", length = 16)
    private String licenseEndStatus;

    @Column(name = "end_reason", length = 32)
    private String endReason;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    // The open Checkout Session, reused until it expires; cleared when it expires or is paid.
    @Column(name = "stripe_checkout_session_id")
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_checkout_url", columnDefinition = "TEXT")
    private String stripeCheckoutUrl;

    @Column(name = "stripe_checkout_expires_at")
    private Instant stripeCheckoutExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    // As last set or seen in Stripe, so a change made there is told apart from our own.
    @Column(name = "is_cancel_at_period_end", nullable = false)
    private boolean isCancelAtPeriodEnd;

    // When the reconciliation job last compared it with Stripe.
    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    // Set when money came in for a subscription that could no longer take it; staff refund it.
    @Column(name = "refund_due_at")
    private Instant refundDueAt;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;
}
