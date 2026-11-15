package com.triphuddle.api.license.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for granting a user or a group a license for free, for a number of its billing periods.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrantSubscriptionRequest {

    // Exactly one of the two, matching the license's target.
    private UUID userId;

    private UUID groupId;

    @NotNull(message = "License ID is required")
    private UUID licenseId;

    @NotNull(message = "Periods is required")
    @Min(value = 1, message = "Periods must be at least 1")
    @Max(value = 120, message = "Periods must be at most 120")
    private Integer periods = 1;
}
