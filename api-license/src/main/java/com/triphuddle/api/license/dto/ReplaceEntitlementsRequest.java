package com.triphuddle.api.license.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body that replaces every entitlement of a license; an empty list removes them all.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceEntitlementsRequest {

    @NotNull(message = "Entitlements are required")
    @Size(max = 50, message = "A license can have at most 50 entitlements")
    private List<@Valid EntitlementRequest> entitlements;
}
