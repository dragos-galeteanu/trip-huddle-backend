package com.triphuddle.api.license.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for subscribing the caller, or a group they administer, to a license, optionally with a promotion code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequest {

    @NotNull(message = "License ID is required")
    private UUID licenseId;

    // Required for a group license, which the caller must administer; not allowed for a user license.
    private UUID groupId;

    @Size(max = 32, message = "Code must be at most 32 characters")
    private String code;
}
