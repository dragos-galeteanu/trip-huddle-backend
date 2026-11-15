package com.triphuddle.api.user.security;

import com.triphuddle.api.user.dto.RegisterUserDeviceRequest;
import com.triphuddle.api.user.dto.UnregisterUserDeviceRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Every signed-in user registers and unregisters their own devices, and only theirs.
 */
@Component("UserDevicePolicy")
public class UserDeviceAuthorizationPolicy
        implements BaseAuthorizationPolicy<RegisterUserDeviceRequest, UnregisterUserDeviceRequest, String> {

    @Override
    public boolean canCreate(Authentication authentication, RegisterUserDeviceRequest request) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, String id) {
        return false;
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return false;
    }

    @Override
    public boolean canUpdate(Authentication authentication, UnregisterUserDeviceRequest request) {
        // Unregistering only ever touches a token of the caller, so being signed in is enough.
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, String id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
