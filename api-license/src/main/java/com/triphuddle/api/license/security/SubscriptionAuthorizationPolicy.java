package com.triphuddle.api.license.security;

import com.triphuddle.api.license.dto.SubscribeRequest;
import com.triphuddle.api.license.entity.LicenseTarget;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.repository.GroupAdminRepository;
import com.triphuddle.api.license.repository.SubscriptionRepository;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Customers subscribe and manage their own subscriptions, and group admins those of their groups;
 * Support and Admin see all of them and activate, grant and extend, and Inspectors see all of them.
 */
@Component("SubscriptionPolicy")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionAuthorizationPolicy implements BaseAuthorizationPolicy<SubscribeRequest, Void, UUID> {

    private final SubscriptionRepository subscriptionRepository;
    private final GroupAdminRepository groupAdminRepository;

    @Override
    public Optional<UUID> resolveOwnerUserId(UUID id) {
        // A group subscription has no single owning user; its group's admins are checked separately.
        return subscriptionRepository.findByIdAndDeletedAtIsNull(id)
                .filter(subscription -> subscription.getSubscriberType() == LicenseTarget.USER)
                .map(Subscription::getSubscriberId);
    }

    @Override
    public boolean canCreate(Authentication authentication, SubscribeRequest request) {
        return request.getGroupId() == null
                ? isAuthenticated(authentication)
                : isGroupAdmin(authentication, request.getGroupId());
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication) || isSubscriberOrItsAdmin(authentication, id);
    }

    /**
     * Check if the user can see a group's subscriptions.
     *
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true if the user is Admin, Support, Inspector or an admin of the group
     */
    public boolean canGetForGroup(Authentication authentication, UUID groupId) {
        return isAdminOrSupportOrInspector(authentication) || isGroupAdmin(authentication, groupId);
    }

    /**
     * Check if the user administers a group, by the local copy of api-group's memberships.
     *
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true if the user has an ADMIN membership of the group
     */
    public boolean isGroupAdmin(Authentication authentication, UUID groupId) {
        return isAuthenticated(authentication) && groupId != null
                && groupAdminRepository.existsByGroupIdAndUserId(groupId, SecurityUtils.extractUserId(authentication));
    }

    private boolean isSubscriberOrItsAdmin(Authentication authentication, UUID id) {
        if (isResourceOwner(authentication, id)) {
            return true;
        }
        return subscriptionRepository.findByIdAndDeletedAtIsNull(id)
                .filter(subscription -> subscription.getSubscriberType() == LicenseTarget.GROUP)
                .map(subscription -> isGroupAdmin(authentication, subscription.getSubscriberId()))
                .orElse(false);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    /**
     * Check if the user can list their own subscriptions.
     *
     * @param authentication the authentication object
     * @return true if the user is signed in
     */
    public boolean canGetMine(Authentication authentication) {
        return isAuthenticated(authentication);
    }

    /**
     * Check if the user can cancel or resume a subscription.
     *
     * @param authentication the authentication object
     * @param id the ID of the subscription
     * @return true if the user is staff, the subscriber, or an admin of the subscribed group
     */
    public boolean canChangeRenewal(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication) || isSubscriberOrItsAdmin(authentication, id);
    }

    /**
     * Check if the user can activate, grant or extend subscriptions.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin or Support
     */
    public boolean canManage(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        return false;
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
