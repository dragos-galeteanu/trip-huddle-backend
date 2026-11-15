package com.triphuddle.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for stopping push notifications on one of the caller's devices.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnregisterUserDeviceRequest {

    @NotBlank(message = "Token is required")
    @Size(max = 4096, message = "Token cannot exceed 4096 characters")
    private String token;
}
