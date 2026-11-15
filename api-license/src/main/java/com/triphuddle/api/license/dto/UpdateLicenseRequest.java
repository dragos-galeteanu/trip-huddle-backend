package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.BillingInterval;
import com.triphuddle.api.license.entity.LicenseTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating a license; code and target can only change while it is a draft.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLicenseRequest {

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "Code must be UPPER_SNAKE_CASE, 2 to 64 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String name;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    @NotNull(message = "Target is required")
    private LicenseTarget target;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must be at least 0")
    private Long priceMinor;

    @NotNull(message = "Billing interval is required")
    private BillingInterval billingInterval;

}
