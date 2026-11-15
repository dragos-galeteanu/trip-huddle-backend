package com.triphuddle.api.license.mapper;

import com.triphuddle.api.license.dto.PromotionResponse;
import com.triphuddle.api.license.dto.PromotionSummary;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.PromotionState;
import com.triphuddle.api.license.service.PriceCalculator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Maps {@link Promotion} to the full response staff see and the summary customers see.
 */
@Component
public class PromotionMapper {

    /**
     * Convert a promotion to its staff response.
     *
     * @param promotion the promotion, with its license
     * @param now the moment its state and price are worked out for
     * @return the response
     */
    public PromotionResponse toResponse(Promotion promotion, Instant now) {
        long discountedPriceMinor = PriceCalculator.discount(promotion.getLicense().getPriceMinor(), promotion);
        return new PromotionResponse(promotion.getId(), promotion.getLicense().getId(), promotion.getName(),
                promotion.getCode(), promotion.getDiscountType(), promotion.getDiscountValue(),
                promotion.getStartsAt(), promotion.getEndsAt(), promotion.getDurationPeriods(),
                promotion.getMaxRedemptions(), promotion.getRedemptionCount(), promotion.isOncePerCustomer(),
                promotion.isNewCustomersOnly(), promotion.getMinPriceMinor(), discountedPriceMinor,
                PromotionState.of(promotion, now), promotion.getCreatedAt(), promotion.getUpdatedAt());
    }

    /**
     * Convert a promotion to the terms a customer sees.
     *
     * @param promotion the promotion; its code only shows if it has one, as the customer entered it
     * @return the summary
     */
    public PromotionSummary toSummary(Promotion promotion) {
        return new PromotionSummary(promotion.getId(), promotion.getName(), promotion.getCode(),
                promotion.getDiscountType(), promotion.getDiscountValue(), promotion.getDurationPeriods(),
                promotion.getStartsAt(), promotion.getEndsAt(), promotion.isOncePerCustomer(),
                promotion.isNewCustomersOnly());
    }
}
