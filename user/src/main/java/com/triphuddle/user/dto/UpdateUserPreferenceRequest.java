package com.triphuddle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPreferenceRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Language code is required")
    @Size(min = 2, max = 2, message = "Language code must be 2 characters")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Language code must be a two-letter ISO 639-1 code")
    private String languageCode;

    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be a two-letter ISO 3166-1 alpha-2 code")
    private String countryCode;

    @NotNull(message = "Marketing opt-in is required")
    private Boolean marketingOptIn;
}
