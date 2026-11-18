package com.triphuddle.api.license.service;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.api.license.entity.PromotionState;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIC-3's discounts and LIC-6's calendar periods, which need nothing but arithmetic.
 */
class PricingRulesTest {

    private final License license = LicenseFixtures.license(LicenseTarget.USER, LicenseStatus.PUBLISHED, 999);

    @Nested
    class Discounts {

        @ParameterizedTest(name = "{0} {1} off {2} with floor {3} costs {4}")
        @CsvSource({
                "AMOUNT,     200, 999,   0, 799",
                "AMOUNT,    2000, 999,   0,   0",
                "AMOUNT,     800, 999, 300, 300",
                "PERCENTAGE,  15, 999,   0, 849",
                "PERCENTAGE,  50, 999, 600, 600",
                "PERCENTAGE, 100, 999,   0,   0",
                "AMOUNT,     100, 999, 5000, 999"
        })
        void neverGoBelowTheFloorNorAboveTheListPrice(DiscountType type, long value, long list, long floor,
                                                      long expected) {
            Promotion promotion = LicenseFixtures.promotion(license, type, value, floor);

            assertThat(PriceCalculator.discount(list, promotion)).isEqualTo(expected);
        }

        @Test
        void roundAPercentageHalfUp() {
            assertThat(PriceCalculator.discount(1, LicenseFixtures.promotion(license, DiscountType.PERCENTAGE, 50, 0)))
                    .as("half of 1 rounds up to 1 off").isZero();
        }
    }

    @Nested
    class PromotionStates {

        private final Instant now = Instant.parse("2026-06-01T12:00:00Z");

        @Test
        void runsFromItsStartUntilItsEndOrItsLastRedemption() {
            Promotion promotion = LicenseFixtures.promotion(license, DiscountType.AMOUNT, 100, 0);
            promotion.setStartsAt(now.minusSeconds(10));

            assertThat(PromotionState.of(promotion, now)).isEqualTo(PromotionState.RUNNING);
            promotion.setMaxRedemptions(2);
            promotion.setRedemptionCount(2);
            assertThat(PromotionState.of(promotion, now)).isEqualTo(PromotionState.EXHAUSTED);
            promotion.setEndsAt(now);
            assertThat(PromotionState.of(promotion, now)).as("an end at this instant is past").isEqualTo(
                    PromotionState.ENDED);
            promotion.setStartsAt(now.plusSeconds(1));
            promotion.setEndsAt(null);
            assertThat(PromotionState.of(promotion, now)).isEqualTo(PromotionState.SCHEDULED);
        }

        @Test
        void overlapsAnyRunSharingAMomentWithIt() {
            Promotion promotion = LicenseFixtures.promotion(license, DiscountType.AMOUNT, 100, 0);
            promotion.setStartsAt(now);
            promotion.setEndsAt(now.plusSeconds(100));

            assertThat(promotion.overlaps(now.plusSeconds(50), null)).isTrue();
            assertThat(promotion.overlaps(now.minusSeconds(50), now.plusSeconds(1))).isTrue();
            assertThat(promotion.overlaps(now.plusSeconds(100), null)).as("starting as it ends").isFalse();
            assertThat(promotion.overlaps(now.minusSeconds(50), now)).as("ending as it starts").isFalse();
            promotion.setEndsAt(null);
            assertThat(promotion.overlaps(now.plusSeconds(10_000), null)).isTrue();
            assertThat(promotion.hasCode()).isFalse();
        }
    }

    @Nested
    class Periods {

        @ParameterizedTest(name = "{1} x{2} from {0} ends {3}")
        @CsvSource({
                "2026-01-31T10:00:00Z, MONTH,  1, 2026-02-28T10:00:00Z",
                "2028-01-31T10:00:00Z, MONTH,  1, 2028-02-29T10:00:00Z",
                "2026-03-15T00:00:00Z, MONTH,  3, 2026-06-15T00:00:00Z",
                "2026-02-28T00:00:00Z, YEAR,   1, 2027-02-28T00:00:00Z",
                "2026-01-01T00:00:00Z, YEAR,  10, 2036-01-01T00:00:00Z"
        })
        void followTheUtcCalendar(Instant start, BillingInterval interval, int periods, Instant end) {
            assertThat(interval.addTo(start, periods)).isEqualTo(end);
        }
    }
}
