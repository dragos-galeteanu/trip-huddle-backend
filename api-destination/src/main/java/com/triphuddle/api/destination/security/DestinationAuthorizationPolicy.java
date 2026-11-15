package com.triphuddle.api.destination.security;

import com.triphuddle.api.destination.service.AccessService;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Everything needs a license with the access entitlement, the caller's own or, when acting for a group, the
 * group's; Admin, Support and Inspector always read, and Admin and Support manage places and categories.
 */
@Component("DestinationPolicy")
@RequiredArgsConstructor
public class DestinationAuthorizationPolicy implements BaseAuthorizationPolicy<Void, Void, UUID> {

    private final AccessService accessService;

    /**
     * Check if the user may browse places and categories.
     *
     * @param authentication the authentication object
     * @param groupId the group the user acts for, or null for themselves
     * @return true for staff, or a user whose license, or group's license, gives access
     */
    public boolean canBrowse(Authentication authentication, UUID groupId) {
        if (isAdminOrSupportOrInspector(authentication)) {
            return true;
        }
        return isAuthenticated(authentication)
                && accessService.hasAccess(SecurityUtils.extractUserId(authentication), groupId);
    }

    /**
     * Check if the user may change places and categories.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin or Support
     */
    public boolean canManage(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    /**
     * Check if the user sees what customers don't, such as hidden places.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin, Support or Inspector
     */
    public boolean isStaff(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canCreate(Authentication authentication, Void request) {
        return canManage(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return canBrowse(authentication, null);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return canBrowse(authentication, null);
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        return canManage(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return canManage(authentication);
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        // Hiding replaces disabling.
        return false;
    }
}
