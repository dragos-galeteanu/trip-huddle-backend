package com.triphuddle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @NotNull(message = "ID is required")
    private UUID id;

    private String bio;

    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String countryCode;

    private String profilePictureUrl;

    private String coverPictureUrl;

    private String xUrl;

    private String facebookUrl;

    private String instagramUrl;

    private String websiteUrl;
}
