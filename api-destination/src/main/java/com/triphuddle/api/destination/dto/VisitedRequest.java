package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Marking a place as visited.
 */
@Data
@NoArgsConstructor
public class VisitedRequest {

    @PastOrPresent
    private LocalDate visitedOn;
}
