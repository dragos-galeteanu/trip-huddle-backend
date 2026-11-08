package com.triphuddle.common.filter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter for pagination parameters in API requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationFilter {

    /**
     * Page number (1-indexed, minimum 1).
     */
    @Min(value = 1, message = "Page must be at least 1")
    private int page;

    /**
     * Number of items per page (minimum 1, maximum 100, defaults to 10).
     */
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int pageSize;
}
