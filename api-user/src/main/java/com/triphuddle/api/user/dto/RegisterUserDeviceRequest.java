package com.triphuddle.api.user.dto;

import com.triphuddle.api.user.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for registering the caller's device for push notifications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserDeviceRequest {

    @NotBlank(message = "Token is required")
    @Size(max = 4096, message = "Token cannot exceed 4096 characters")
    private String token;

    @NotNull(message = "Platform is required")
    private DevicePlatform platform;
}
