package com.triphuddle.api.payment.entity;

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

/**
 * A verified Stripe webhook event, stored raw as received and handled later by {@code StripeEventProcessor}.
 */
@Entity
@Table(
        name = "payment_stripe_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_stripe_event_stripe_event", columnNames = "stripe_event_id")
)
@Getter
@Setter
@NoArgsConstructor
public class StripeEvent extends BaseJpaEntity {

    @Column(name = "stripe_event_id", nullable = false)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "api_version", length = 64)
    private String apiVersion;

    @Column(name = "is_livemode", nullable = false)
    private boolean isLivemode;

    @Column(name = "stripe_created_at", nullable = false)
    private Instant stripeCreatedAt;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StripeEventStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
