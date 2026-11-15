package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A name a place is known by in a language, or an alias.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceNameRequest {

    /**
     * An ISO 639-1 code, or {@code und} for no particular language.
     */
    @NotBlank
    @Pattern(regexp = "^([a-z]{2}|und)$")
    private String languageCode;

    @NotBlank
    @Size(max = 200)
    private String name;

    private Boolean isAlias = false;
}
