package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Why a review should not be shown.
 */
@Data
@NoArgsConstructor
public class ReviewReportRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
