package com.triphuddle.api.license.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;

/**
 * A discount on a license from a start date until an optional end date; automatic unless it has a code.
 */
// Loads the licenses or promotions of a page of subscriptions in a few queries rather than one each.
@BatchSize(size = 50)
@Entity
// The code is unique within a license among promotions not deleted: a partial index, which JPA can't declare.
@Table(name = "license_promotions")
@Getter
@Setter
@NoArgsConstructor
public class Promotion extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    // Stored in upper case; null means the promotion applies without a code.
    @Column(name = "code", length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private DiscountType discountType;

    // Minor units off for AMOUNT, 1 to 100 for PERCENTAGE.
    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    // How many billing periods are discounted; null means every period.
    @Column(name = "duration_periods")
    private Integer durationPeriods;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;

    @Column(name = "is_once_per_customer", nullable = false)
    private boolean isOncePerCustomer;

    @Column(name = "is_new_customers_only", nullable = false)
    private boolean isNewCustomersOnly;

    // The discounted price never goes below this, nor above the list price.
    @Column(name = "min_price_minor", nullable = false)
    private long minPriceMinor;

    /**
     * Check if the promotion needs a code to apply.
     *
     * @return true if it has a code
     */
    public boolean hasCode() {
        return code != null;
    }

    /**
     * Check if the promotion runs at the given moment and has redemptions left.
     *
     * @param at the moment to check
     * @return true if it can be redeemed then
     */
    public boolean isRedeemableAt(Instant at) {
        return !startsAt.isAfter(at)
                && (endsAt == null || endsAt.isAfter(at))
                && (maxRedemptions == null || redemptionCount < maxRedemptions);
    }

    /**
     * Check if the promotion's run overlaps another's; a missing end runs forever.
     *
     * @param otherStartsAt when the other run starts
     * @param otherEndsAt when the other run ends, or null if it never does
     * @return true if both runs share a moment
     */
    public boolean overlaps(Instant otherStartsAt, Instant otherEndsAt) {
        return (otherEndsAt == null || startsAt.isBefore(otherEndsAt))
                && (endsAt == null || otherStartsAt.isBefore(endsAt));
    }
}
