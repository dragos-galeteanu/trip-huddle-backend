package com.triphuddle.api.license.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entitlement of a license: its key and, unless it is a plain flag, its value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementRequest {

    @NotBlank(message = "Key is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$", message = "Key must be UPPER_SNAKE_CASE, at most 64 characters")
    private String key;

    @Size(max = 255, message = "Value must be at most 255 characters")
    private String value;
}
