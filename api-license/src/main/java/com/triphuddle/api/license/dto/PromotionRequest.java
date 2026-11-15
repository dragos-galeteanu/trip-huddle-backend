package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request body for creating or updating a promotion; without a code it applies automatically.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String name;

    @Pattern(regexp = "(?i)^[A-Z0-9][A-Z0-9_-]{2,31}$",
            message = "Code must be 3 to 32 letters, digits, '_' or '-', starting with a letter or digit")
    private String code;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be at least 1")
    private Long discountValue;

    @NotNull(message = "Start is required")
    private Instant startsAt;

    private Instant endsAt;

    @Positive(message = "Duration must be at least 1 period")
    private Integer durationPeriods;

    @Positive(message = "Max redemptions must be at least 1")
    private Integer maxRedemptions;

    @NotNull(message = "Once per customer is required")
    private Boolean isOncePerCustomer = false;

    @NotNull(message = "New customers only is required")
    private Boolean isNewCustomersOnly = false;

    @NotNull(message = "Minimum price is required")
    @PositiveOrZero(message = "Minimum price must be at least 0")
    private Long minPriceMinor = 0L;
}
