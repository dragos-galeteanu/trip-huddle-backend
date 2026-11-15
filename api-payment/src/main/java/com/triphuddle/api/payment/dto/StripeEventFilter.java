package com.triphuddle.api.payment.dto;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.api.payment.entity.StripeEventStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The cursor, page size and filters of the stored Stripe events; a missing filter keeps every event.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StripeEventFilter extends CursorPaginationFilter {

    private StripeEventStatus status;

    /**
     * A Stripe event type, e.g. {@code invoice.paid}.
     */
    private String eventType;
}
