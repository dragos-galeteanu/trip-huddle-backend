package com.triphuddle.api.payment.dto;

import com.triphuddle.common.filter.PaginationFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The page and filters of the staff invoice list; a missing filter keeps every invoice.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InvoiceFilter extends PaginationFilter {

    private UUID subscriberId;

    private UUID licenseSubscriptionId;

    /**
     * Stripe's status: {@code open}, {@code paid}, {@code void} or {@code uncollectible}.
     */
    private String status;
}
