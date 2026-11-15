package com.triphuddle.api.license.service;

import com.triphuddle.api.license.entity.Promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The price arithmetic of promotions, in minor units.
 */
public final class PriceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PriceCalculator() {}

    /**
     * Apply a promotion to a list price: the discount, then the promotion's floor.
     *
     * @param listPriceMinor the price without the promotion
     * @param promotion the promotion
     * @return the discounted price, between the floor and the list price
     */
    public static long discount(long listPriceMinor, Promotion promotion) {
        long discounted = switch (promotion.getDiscountType()) {
            case AMOUNT -> listPriceMinor - promotion.getDiscountValue();
            // Half up, so 15% off 999 is 150 off, not 149.
            case PERCENTAGE -> listPriceMinor - BigDecimal.valueOf(listPriceMinor)
                    .multiply(BigDecimal.valueOf(promotion.getDiscountValue()))
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                    .longValueExact();
        };
        // A floor above the list price must not make the promotion raise the price.
        long floor = Math.min(promotion.getMinPriceMinor(), listPriceMinor);
        return Math.max(discounted, floor);
    }
}
