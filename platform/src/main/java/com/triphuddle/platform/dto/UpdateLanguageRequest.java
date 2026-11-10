package com.triphuddle.platform.dto;

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
public class UpdateLanguageRequest {

    @NotNull(message = "ID is required")
    private UUID id;

    @NotBlank(message = "Code is required")
    @Size(min = 2, max = 8, message = "Code must be between 2 and 8 characters")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Native name is required")
    private String nativeName;

    @NotNull(message = "Right-to-left is required")
    private Boolean isRightToLeft;
}
