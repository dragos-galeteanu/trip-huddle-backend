package com.triphuddle.api.payment.security;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.dto.CheckoutRequest;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.api.payment.repository.GroupAdminRepository;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A user pays for their own subscriptions, and any admin of a group for the group's.
 */
@Component("CheckoutPolicy")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutAuthorizationPolicy implements BaseAuthorizationPolicy<CheckoutRequest, Void, Void> {

    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final GroupAdminRepository groupAdminRepository;

    @Override
    public boolean canCreate(Authentication authentication, CheckoutRequest request) {
        if (!isAuthenticated(authentication) || request == null || request.getSubscriptionId() == null) {
            return false;
        }
        Optional<BillingSubscription> subscription =
                billingSubscriptionRepository.findByLicenseSubscriptionId(request.getSubscriptionId())
                        .filter(found -> found.getSubscriberId() != null);
        if (subscription.isEmpty()) {
            // Not here yet, or not the caller's to know about; the service answers not found.
            return true;
        }
        BillingSubscription found = subscription.get();
        return found.getSubscriberType() == LicenseTarget.USER
                ? isCurrentUser(authentication, found.getSubscriberId())
                : groupAdminRepository.existsByGroupIdAndUserId(found.getSubscriberId(),
                        SecurityUtils.extractUserId(authentication));
    }

    @Override
    public boolean canGetById(Authentication authentication, Void id) {
        return false;
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return false;
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        return false;
    }

    @Override
    public boolean canDelete(Authentication authentication, Void id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
