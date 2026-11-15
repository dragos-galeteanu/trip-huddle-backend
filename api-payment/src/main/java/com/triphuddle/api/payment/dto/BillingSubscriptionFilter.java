package com.triphuddle.api.payment.dto;

import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The page and filters of the staff list of billing subscriptions; a missing filter keeps every one.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BillingSubscriptionFilter extends PaginationFilter {

    private BillingSubscriptionStatus status;

    private UUID subscriberId;

    /**
     * True for only those whose payment couldn't be taken and waits for a refund.
     */
    private Boolean isRefundDue;
}
