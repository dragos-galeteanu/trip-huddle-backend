package com.triphuddle.api.license.mapper;

import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.dto.EntitlementResponse;
import com.triphuddle.api.license.dto.SubscriptionResponse;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps {@link Subscription} to its response, with its license's current entitlements.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionMapper {

    private final LicenseProperties licenseProperties;
    private final PromotionMapper promotionMapper;

    /**
     * Convert a subscription to its response.
     *
     * @param subscription the subscription, with its license and promotion
     * @return the response
     */
    public SubscriptionResponse toResponse(Subscription subscription) {
        License license = subscription.getLicense();
        List<EntitlementResponse> entitlements = license.getEntitlements().stream()
                .map(entitlement -> new EntitlementResponse(entitlement.getEntitlementKey(),
                        entitlement.getEntitlementValue()))
                .toList();
        return new SubscriptionResponse(subscription.getId(), license.getId(), license.getCode(), license.getName(),
                subscription.getSubscriberType(), subscription.getSubscriberId(), subscription.getPurchasedByUserId(),
                subscription.getStatus(), licenseProperties.currency(), subscription.getBillingInterval(),
                subscription.getListPriceMinor(), subscription.getPriceMinor(),
                subscription.getPromotion() == null ? null : promotionMapper.toSummary(subscription.getPromotion()),
                subscription.getPromotionPeriodsRemaining(), subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(), subscription.isAutoRenew(), subscription.getRenewalCount(),
                subscription.getCanceledAt(), subscription.getActivatedAt(), subscription.getEndedAt(),
                subscription.getEndReason(), subscription.getSwitchedToSubscriptionId(), subscription.isGranted(),
                subscription.getGrantedByUserId(), entitlements, subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }

    /**
     * Convert subscriptions to their responses.
     *
     * @param subscriptions the subscriptions
     * @return the responses, in the same order
     */
    public List<SubscriptionResponse> toResponseList(List<Subscription> subscriptions) {
        return subscriptions.stream().map(this::toResponse).toList();
    }
}
