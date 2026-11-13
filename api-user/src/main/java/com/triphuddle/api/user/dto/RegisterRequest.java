package com.triphuddle.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for registering the signed-in Firebase account as a platform user.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
