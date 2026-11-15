package com.triphuddle.api.license.entity;

import java.time.Instant;

/**
 * Where a promotion's run stands at a moment; derived, not stored.
 */
public enum PromotionState {
    SCHEDULED,
    RUNNING,
    EXHAUSTED,
    ENDED;

    /**
     * Derive the state of a promotion at a moment.
     *
     * @param promotion the promotion
     * @param at the moment
     * @return its state then
     */
    public static PromotionState of(Promotion promotion, Instant at) {
        if (promotion.getEndsAt() != null && !promotion.getEndsAt().isAfter(at)) {
            return ENDED;
        }
        if (promotion.getStartsAt().isAfter(at)) {
            return SCHEDULED;
        }
        return promotion.isRedeemableAt(at) ? RUNNING : EXHAUSTED;
    }
}
