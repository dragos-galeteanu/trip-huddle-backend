package com.triphuddle.common.filter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cursor pagination parameters: the opaque cursor from the previous page and the page size.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPaginationFilter {

    /**
     * The {@code nextCursor} of the previous page, or null for the first page.
     */
    private String cursor;

    /**
     * Number of items per page (minimum 1, maximum 100, defaults to 20).
     */
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    private int limit = 20;
}
