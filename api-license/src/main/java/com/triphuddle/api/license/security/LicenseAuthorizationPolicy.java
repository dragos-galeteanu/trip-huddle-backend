package com.triphuddle.api.license.security;

import com.triphuddle.api.license.dto.CreateLicenseRequest;
import com.triphuddle.api.license.dto.UpdateLicenseRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The license catalog is readable by anyone; only Support and Admin create and manage licenses,
 * and Inspectors also read promotions.
 */
@Component("LicensePolicy")
public class LicenseAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateLicenseRequest, UpdateLicenseRequest, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, CreateLicenseRequest request) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        // The service hides licenses that are not published from everyone but staff.
        return true;
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return true;
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateLicenseRequest request) {
        return isAdminOrSupport(authentication);
    }

    /**
     * Check if the user can replace a license's entitlements, publish it or archive it.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin or Support
     */
    public boolean canManage(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    /**
     * Check if the user can read a license's promotions, which reveal their codes.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin, Support or Inspector
     */
    public boolean canReadPromotions(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        // The lifecycle replaces disabling: archive a license to stop new subscriptions.
        return false;
    }
}
