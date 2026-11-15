package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A question about a place.
 */
@Data
@NoArgsConstructor
public class QuestionRequest {

    @NotBlank
    @Size(max = 1000)
    private String question;

    @Pattern(regexp = "^[a-zA-Z]{2}$")
    private String language;
}
