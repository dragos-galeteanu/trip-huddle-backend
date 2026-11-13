package com.triphuddle.api.platform.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reference data: readable by anyone, authenticated or not, writable by Admin or Support only.
 */
@Component("CountryPolicy")
public class CountryAuthorizationPolicy implements BaseAuthorizationPolicy<CreateCountryRequest, UpdateCountryRequest, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, CreateCountryRequest createCountryRequest) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return true;
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return true;
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateCountryRequest updateCountryRequest) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication);
    }
}
