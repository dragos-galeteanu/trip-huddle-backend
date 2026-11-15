package com.triphuddle.api.license.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's or group's subscription to a license, with the price and interval it was promised.
 */
@Entity
@Table(name = "license_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", nullable = false, length = 16)
    private LicenseTarget subscriberType;

    // A user ID or a group ID, by subscriber type.
    @Column(name = "subscriber_id", nullable = false)
    private UUID subscriberId;

    @Column(name = "purchased_by_user_id", nullable = false)
    private UUID purchasedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.PENDING_PAYMENT;

    // Price and interval are copied from the license, so later changes to it don't reach this subscription.
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 16)
    private BillingInterval billingInterval;

    @Column(name = "list_price_minor", nullable = false)
    private long listPriceMinor;

    // What the current period costs: the promoted price while promoted periods are left, else the list price.
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    // Promoted periods left, the current one included; null with a promotion means every period.
    @Column(name = "promotion_periods_remaining")
    private Integer promotionPeriodsRemaining;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "is_auto_renew", nullable = false)
    private boolean isAutoRenew = true;

    @Column(name = "renewal_count", nullable = false)
    private int renewalCount;

    // When auto-renew was turned off; the subscription still runs to the end of its period.
    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activated_by_user_id")
    private UUID activatedByUserId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private SubscriptionEndReason endReason;

    @Column(name = "switched_to_subscription_id")
    private UUID switchedToSubscriptionId;

    // The period end a renewal reminder was sent for; another one goes out if the period moves.
    @Column(name = "reminded_period_end")
    private Instant remindedPeriodEnd;

    @Column(name = "is_granted", nullable = false)
    private boolean isGranted;

    @Column(name = "granted_by_user_id")
    private UUID grantedByUserId;
}
