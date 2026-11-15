package com.triphuddle.api.payment.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stored Stripe events are staff only: Admin, Support and Inspector read them, Admin and Support replay them.
 */
@Component("StripeEventPolicy")
public class StripeEventAuthorizationPolicy implements BaseAuthorizationPolicy<Void, Void, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, Void request) {
        // Events come only from Stripe, through the webhook.
        return false;
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    /**
     * Check if the user can queue a failed or ignored event to be handled again.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin or Support
     */
    public boolean canReplay(Authentication authentication) {
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
