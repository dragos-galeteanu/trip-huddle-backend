package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * A review of a place, as its author writes it.
 */
@Data
@NoArgsConstructor
public class ReviewRequest {

    @Min(1)
    @Max(5)
    private int rating;

    @Size(max = 120)
    private String title;

    @Size(max = 4000)
    private String body;

    @PastOrPresent
    private LocalDate visitedOn;

    @Pattern(regexp = "^[a-zA-Z]{2}$")
    private String language;
}
