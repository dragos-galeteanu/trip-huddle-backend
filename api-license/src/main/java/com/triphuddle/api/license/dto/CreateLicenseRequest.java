package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.BillingInterval;
import com.triphuddle.api.license.entity.LicenseTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for creating a license, as a draft.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLicenseRequest {

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

    @Size(max = 50, message = "A license can have at most 50 entitlements")
    private List<@Valid EntitlementRequest> entitlements = new ArrayList<>();
}
