package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.SubscriptionStatus;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The page and filters of a subscription list; a missing filter keeps every subscription.
 * Subscriber and license only apply to the staff list.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SubscriptionFilter extends PaginationFilter {

    private SubscriptionStatus status;

    private UUID subscriberId;

    private UUID licenseId;
}
